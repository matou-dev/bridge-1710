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
        Remapper remapper = new Remapper() {
            public String mapMethodName(String owner, String name, String desc) {
                if (owner.startsWith("net/minecraft/")) {
                    String hit = m.get(owner + "." + name + desc);
                    if (hit != null) {
                        return hit;
                    }
                }
                return name;
            }
            public String mapFieldName(String owner, String name, String desc) {
                if (owner.startsWith("net/minecraft/")) {
                    String hit = f.get(owner + "." + name);
                    if (hit != null) {
                        return hit;
                    }
                }
                return name;
            }
        };
        JarFile in = new JarFile(a[1]);
        JarOutputStream out = new JarOutputStream(new FileOutputStream(a[2]));
        Enumeration<JarEntry> en = in.entries();
        int remappedRefs = 0;
        while (en.hasMoreElements()) {
            JarEntry e = en.nextElement();
            InputStream is = in.getInputStream(e);
            byte[] data = readAll(is);
            is.close();
            JarEntry ne = new JarEntry(e.getName());
            ne.setTime(e.getTime());
            out.putNextEntry(ne);
            if (e.getName().endsWith(".class")) {
                ClassReader cr = new ClassReader(data);
                ClassWriter cw = new ClassWriter(0);
                cr.accept(new RemappingClassAdapter(cw, remapper), ClassReader.EXPAND_FRAMES);
                out.write(cw.toByteArray());
            } else {
                out.write(data);
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
