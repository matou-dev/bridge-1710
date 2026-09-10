import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** B3 prototype: reobfuscate MCP-named member refs to SRG (ForgeGradle reobf equivalent). */
public final class Reobf {
    public static void main(String[] a) throws Exception {
        if (a.length != 3) {
            System.err.println("usage: Reobf <srg-mcp.srg> <in.jar> <out.jar>");
            System.exit(1);
        }
        Map<String, String> methods = new HashMap<String, String>();
        Map<String, String> fields = new HashMap<String, String>();
        BufferedReader br = new BufferedReader(new FileReader(a[0]));
        String line;
        while ((line = br.readLine()) != null) {
            String[] t = line.split(" ");
            if (t[0].equals("MD:") && t.length == 5) {
                methods.put(mcpKey(t[3], t[4]), simple(t[1]));
            } else if (t[0].equals("FD:") && t.length == 3) {
                String fqn = t[2];
                int s = fqn.lastIndexOf('/');
                fields.put(fqn.substring(0, s) + "." + fqn.substring(s + 1), simple(t[1]));
            }
        }
        br.close();
        System.err.println("map: " + methods.size() + " methods, " + fields.size() + " fields");
        final Map<String, String> m = methods;
        final Map<String, String> f = fields;
        // In-jar superclass chain (internal names): forge/ subclasses
        // vanilla (MatouBlock extends Block) and inherited member refs
        // compile with the project class as owner. An owner-blind map
        // leaves the MCP name in place and dies linking live (measured:
        // NoSuchMethodError MatouBlock.setBlockName at preInit). Owners
        // outside net/minecraft/ walk this chain; the first SRG hit wins.
        // Overriding declarations flow through the same hooks
        // (ClassRemapper visits them), so isOpaqueCube links too.
        // Interface defaults are NOT walked (no forge/ interface impls).
        JarFile in = new JarFile(a[1]);
        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        List<String> order = new ArrayList<String>();
        Enumeration<JarEntry> scan = in.entries();
        while (scan.hasMoreElements()) {
            JarEntry e = scan.nextElement();
            InputStream is = in.getInputStream(e);
            byte[] data = readAll(is);
            is.close();
            order.add(e.getName());
            if (e.getName().endsWith(".class")) {
                classes.put(e.getName(), data);
            } else {
                classes.put(e.getName(), null);
            }
        }
        final Map<String, String> supers = new HashMap<String, String>();
        for (byte[] data : classes.values()) {
            if (data == null) {
                continue;
            }
            ClassReader cr = new ClassReader(data);
            supers.put(cr.getClassName(), cr.getSuperName());
        }
        Remapper remapper = new Remapper() {
            private String walk(Map<String, String> map, String owner,
                    String member) {
                String o = owner;
                while (o != null) {
                    String hit = map.get(o + "." + member);
                    if (hit != null) {
                        return hit;
                    }
                    o = supers.get(o);
                }
                return null;
            }
            public String mapMethodName(String owner, String name, String desc) {
                String hit = walk(m, owner, name + desc);
                return hit != null ? hit : name;
            }
            public String mapFieldName(String owner, String name, String desc) {
                String hit = walk(f, owner, name);
                return hit != null ? hit : name;
            }
        };
        JarOutputStream out = new JarOutputStream(new FileOutputStream(a[2]));
        for (String entryName : order) {
            byte[] data = classes.get(entryName);
            out.putNextEntry(new JarEntry(entryName));
            if (data != null) {
                ClassReader cr = new ClassReader(data);
                ClassWriter cw = new ClassWriter(0);
                cr.accept(new RemappingClassAdapter(cw, remapper), ClassReader.EXPAND_FRAMES);
                out.write(cw.toByteArray());
            } else {
                InputStream is = in.getInputStream(in.getEntry(entryName));
                out.write(readAll(is));
                is.close();
            }
            out.closeEntry();
        }
        in.close();
        out.close();
        System.out.println("ok reobf : " + a[2]);
    }

    private static String simple(String fqn) {
        return fqn.substring(fqn.lastIndexOf('/') + 1);
    }

    private static String mcpKey(String mcpFqn, String mcpDesc) {
        int s = mcpFqn.lastIndexOf('/');
        return mcpFqn.substring(0, s) + "." + mcpFqn.substring(s + 1) + mcpDesc;
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
