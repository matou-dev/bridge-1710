package fr.iamacat.bridge.forge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.model.BeastAnimation;
import fr.iamacat.bridge.model.BeastModel;
import fr.iamacat.bridge.model.BeastTexture;
import fr.iamacat.bridge.render.RenderJob;
import fr.iamacat.bridge.render.RenderSeal;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.model.MatouAnimation;
import fr.iamacat.spi.model.MatouModel;
import fr.iamacat.spi.model.Molang;
import fr.iamacat.spi.render.GlBackend;
import fr.iamacat.spi.render.InstanceBucket.Rec;
import fr.iamacat.spi.render.InstanceFormat;
import fr.iamacat.spi.render.ViewProjection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

/**
 * Client-only instanced mesh renderer hooked into RenderWorldLastEvent.
 * Renders all visible MatouEntity instances via OpenGL 3.1+ instancing primitives (Lwjgl2Backend).
 * The static mesh is the SPI skinned bake of the shipped
 * {@code my_beast.geo.json} (hub decisions/MATOU_ANIMATION.md) — bind
 * positions plus the file-order bone index per vertex, never a hardcoded
 * box again. Per-instance bone deltas ride a second instanced VBO (GPU
 * skinning over the static VBO — the CPU oracle never uploads).
 *
 * <p>1.7.10 shape (derived from the sha1-pinned srg-mcp.srg, never
 * recalled — same derive discipline as the 1122 narrow map): the client
 * world rides the {@code theWorld} field (SRG {@code field_71441_e},
 * WorldClient-typed), the camera entity rides the
 * {@code renderViewEntity} field (SRG {@code field_71451_h} — a field
 * here, the 1122 {@code getRenderViewEntity()} method does not exist on
 * 1614), and the frame event carries {@code partialTicks} as a public
 * field (pinned against the provisioned universal, same as every Forge
 * member — the 1122 {@code getPartialTicks()} getter does not exist on
 * 1614).
 */
@SideOnly(Side.CLIENT)
public final class InstancedMeshRenderer {
    private static final InstancedMeshRenderer INSTANCE = new InstancedMeshRenderer();

    /**
     * Skinned stride (SPI {@code bakeSkinnedMesh} layout: pos3, uv2,
     * normal3, bone1 — bind positions, bone1 is the file-order bone
     * index as float). The stride-8 {@code bakeMesh} stays the gate
     * oracle layout, never the upload.
     */
    private static final int SKINNED_STRIDE = 9;

    /**
     * Bone ceiling for the per-instance path (2-bone shipped beast:
     * body+head). Each bone costs one instanced mat4 (4 attribute
     * slots); with the 4 static + 4 instance slots the 2-bone proof
     * fills the guaranteed 16 exactly. A third bone refuses loudly at
     * {@code initGl} — the generic palette (bone texture / packed pose)
     * is the named follow-up, never a silent drop.
     */
    private static final int SKINNED_BONES = 2;

    private static final String VERTEX_SHADER =
            "#version 330 core\n"
            + "layout(location = 0) in vec3 a_pos;\n"
            + "layout(location = 1) in vec2 a_uv;\n"
            + "layout(location = 2) in vec3 a_normal;\n"
            + "layout(location = 3) in float a_bone;\n"
            + "layout(location = 4) in vec3 i_pos;\n"
            + "layout(location = 5) in vec3 i_rot_scale;\n"
            + "layout(location = 6) in vec4 i_color;\n"
            + "layout(location = 7) in vec2 i_light;\n"
            + "layout(location = 8) in vec4 i_b0c0;\n"
            + "layout(location = 9) in vec4 i_b0c1;\n"
            + "layout(location = 10) in vec4 i_b0c2;\n"
            + "layout(location = 11) in vec4 i_b0c3;\n"
            + "layout(location = 12) in vec4 i_b1c0;\n"
            + "layout(location = 13) in vec4 i_b1c1;\n"
            + "layout(location = 14) in vec4 i_b1c2;\n"
            + "layout(location = 15) in vec4 i_b1c3;\n"
            + "uniform mat4 u_projection;\n"
            + "uniform mat4 u_view;\n"
            + "out vec4 v_color;\n"
            + "out vec3 v_normal;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    mat4 b0 = mat4(i_b0c0, i_b0c1, i_b0c2, i_b0c3);\n"
            + "    mat4 b1 = mat4(i_b1c0, i_b1c1, i_b1c2, i_b1c3);\n"
            + "    mat4 skin = a_bone < 0.5 ? b0 : b1;\n"
            + "    vec4 posed = skin * vec4(a_pos, 1.0);\n"
            + "    float yaw = i_rot_scale.x;\n"
            + "    float scale = i_rot_scale.z;\n"
            + "    float cy = cos(yaw);\n"
            + "    float sy = sin(yaw);\n"
            + "    mat3 rotY = mat3(cy, 0.0, sy, 0.0, 1.0, 0.0, -sy, 0.0, cy);\n"
            + "    vec3 localPos = rotY * (posed.xyz * scale);\n"
            + "    vec3 worldRelPos = localPos + i_pos;\n"
            + "    gl_Position = u_projection * u_view * vec4(worldRelPos, 1.0);\n"
            + "    v_color = i_color;\n"
            + "    v_normal = normalize(rotY * (mat3(skin) * a_normal));\n"
            + "    v_uv = a_uv;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 330 core\n"
            + "in vec4 v_color;\n"
            + "in vec3 v_normal;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_tex;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 lightDir = normalize(vec3(0.2, 1.0, -0.7));\n"
            + "    float diff = max(dot(v_normal, lightDir), 0.0) * 0.4 + 0.6;\n"
            + "    vec4 col = texture(u_tex, v_uv) * v_color;\n"
            + "    col.rgb *= diff;\n"
            + "    fragColor = col;\n"
            + "}\n";

    private final GlBackend backend;
    private boolean initialized;
    private boolean drawLogged;
    /** Latest camera-valid matrices, captured at chunk-render time (see
     * {@link #onRenderWorldPre}) in driver column-major order, consumed
     * by the {@link #onRenderWorldLast} draw of the same frame. */
    private final float[] frameViewCol = new float[16];
    private final float[] frameProjCol = new float[16];
    private boolean haveFrameMatrices;
    private int program;
    private int vao;
    private int meshVbo;
    private int instanceVbo;
    private int boneVbo;
    private int vertexCount;
    private int uProjLoc;
    private int uViewLoc;
    private int uTexLoc;
    private int texture;
    private final FloatBuffer viewMatrixBuffer;
    private final FloatBuffer projMatrixBuffer;
    private FloatBuffer instanceBuffer;
    /** Per-instance bone deltas (2 mat4 = 32 floats per beast, columns). */
    private FloatBuffer boneBuffer;
    /** Shared-texture bucket key: the V2 shader samples the beast texture
     * and multiplies the tint (hub decisions/MATOU_MODEL.md) — one mesh
     * and one texture today, per-mob textures plug their own keys here
     * (named follow-up, never a silent second texture). */
    private static final String TEXTURE_TINT = "tint";
    private static final RenderJob RENDER_JOB = new RenderJob();
    /** Client frame sequence carried by the render snapshot (the job
     * ignores the tick — no addressed randomness on this path — but a
     * snapshot refuses a negative one, so the frames number it). */
    private long frame;

    private InstancedMeshRenderer() {
        this.backend = new Lwjgl2Backend();
        this.viewMatrixBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.projMatrixBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.instanceBuffer = ByteBuffer.allocateDirect(512 * InstanceFormat.STRIDE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.boneBuffer = ByteBuffer.allocateDirect(512 * SKINNED_BONES * 16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public static InstancedMeshRenderer getInstance() {
        return INSTANCE;
    }

    public static void initClient() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    private void initGl() {
        int vs = backend.createShader(GlBackend.GL_VERTEX_SHADER);
        backend.shaderSource(vs, VERTEX_SHADER);
        backend.compileShader(vs);
        if (!backend.getShaderCompileStatus(vs)) {
            throw new IllegalStateException("E_GL_SHADER:compile " + backend.getShaderInfoLog(vs));
        }

        int fs = backend.createShader(GlBackend.GL_FRAGMENT_SHADER);
        backend.shaderSource(fs, FRAGMENT_SHADER);
        backend.compileShader(fs);
        if (!backend.getShaderCompileStatus(fs)) {
            throw new IllegalStateException("E_GL_SHADER:compile " + backend.getShaderInfoLog(fs));
        }

        program = backend.createProgram();
        backend.attachShader(program, vs);
        backend.attachShader(program, fs);
        backend.linkProgram(program);
        if (!backend.getProgramLinkStatus(program)) {
            throw new IllegalStateException("E_GL_PROGRAM:link " + backend.getProgramInfoLog(program));
        }

        uProjLoc = backend.getUniformLocation(program, "u_projection");
        uViewLoc = backend.getUniformLocation(program, "u_view");
        uTexLoc = backend.getUniformLocation(program, "u_tex");

        vao = backend.genVertexArrays();
        backend.bindVertexArray(vao);

        meshVbo = backend.genBuffers();
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, meshVbo);
        // Animation tranche: the static mesh is the SPI skinned bake of
        // the shipped beast geometry (bind positions + file-order bone
        // index), never a rebaked pose. A missing or broken model, a
        // missing animation file, or a non-2-bone beast refuses here,
        // loudly, before the first frame.
        MatouModel beastModel = BeastModel.cached().model();
        if (beastModel.bones.size() != SKINNED_BONES) {
            throw new IllegalStateException("E_ANIM_SKIN:bones <"
                    + beastModel.bones.size() + "> (the per-instance mat4 "
                    + "path fills the guaranteed 16 attributes at 2 "
                    + "bones — the generic palette is the named "
                    + "follow-up)");
        }
        BeastAnimation.cached();
        float[] mesh = beastModel.bakeSkinnedMesh();
        vertexCount = mesh.length / SKINNED_STRIDE;
        FloatBuffer meshData = ByteBuffer.allocateDirect(mesh.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        meshData.put(mesh);
        meshData.flip();
        backend.bufferData(GlBackend.GL_ARRAY_BUFFER, meshData, GlBackend.GL_STATIC_DRAW);

        int meshStride = SKINNED_STRIDE * 4;
        backend.enableVertexAttribArray(0);
        backend.vertexAttribPointer(0, 3, GlBackend.GL_FLOAT, false, meshStride, 0);
        backend.enableVertexAttribArray(1);
        backend.vertexAttribPointer(1, 2, GlBackend.GL_FLOAT, false, meshStride, 3 * 4);
        backend.enableVertexAttribArray(2);
        backend.vertexAttribPointer(2, 3, GlBackend.GL_FLOAT, false, meshStride, 5 * 4);
        backend.enableVertexAttribArray(3);
        backend.vertexAttribPointer(3, 1, GlBackend.GL_FLOAT, false, meshStride, 8 * 4);

        instanceVbo = backend.genBuffers();
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, instanceVbo);

        int instStride = InstanceFormat.STRIDE_BYTES;
        backend.enableVertexAttribArray(4);
        backend.vertexAttribPointer(4, 3, GlBackend.GL_FLOAT, false, instStride, 0);
        backend.vertexAttribDivisor(4, 1);

        backend.enableVertexAttribArray(5);
        backend.vertexAttribPointer(5, 3, GlBackend.GL_FLOAT, false, instStride, 12);
        backend.vertexAttribDivisor(5, 1);

        backend.enableVertexAttribArray(6);
        backend.vertexAttribPointer(6, 4, GlBackend.GL_FLOAT, false, instStride, 24);
        backend.vertexAttribDivisor(6, 1);

        backend.enableVertexAttribArray(7);
        backend.vertexAttribPointer(7, 2, GlBackend.GL_FLOAT, false, instStride, 40);
        backend.vertexAttribDivisor(7, 1);

        // Per-instance bone deltas (second instanced VBO, STREAM per
        // bucket): 2 mat4 as 8 vec4 columns (SPI row-major transposed
        // once at pack — GL columns, never recomputed).
        boneVbo = backend.genBuffers();
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, boneVbo);
        int boneStride = SKINNED_BONES * 16 * 4;
        for (int b = 0; b < SKINNED_BONES; b++) {
            for (int c = 0; c < 4; c++) {
                int loc = 8 + b * 4 + c;
                backend.enableVertexAttribArray(loc);
                backend.vertexAttribPointer(loc, 4, GlBackend.GL_FLOAT,
                        false, boneStride, (b * 16 + c * 4) * 4);
                backend.vertexAttribDivisor(loc, 1);
            }
        }

        // Texture tranche (hub decisions/MATOU_MODEL.md, V2): the beast
        // texture uploads once, NEAREST + CLAMP_TO_EDGE (MC pixels, no
        // bleed, no mipmaps — NPOT-safe), on unit 0. A missing or broken
        // texture refuses here, loudly, before the first frame — an
        // untextured tint fallback would be a silent pass.
        BeastTexture beastTex = BeastTexture.cached();
        texture = backend.genTextures();
        backend.bindTexture(GlBackend.GL_TEXTURE_2D, texture);
        backend.texImage2D(GlBackend.GL_TEXTURE_2D, 0, GlBackend.GL_RGBA,
                beastTex.width(), beastTex.height(), 0,
                GlBackend.GL_RGBA, GlBackend.GL_UNSIGNED_BYTE,
                beastTex.uploadBuffer());
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_MIN_FILTER, GlBackend.GL_NEAREST);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_MAG_FILTER, GlBackend.GL_NEAREST);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_WRAP_S, GlBackend.GL_CLAMP_TO_EDGE);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_WRAP_T, GlBackend.GL_CLAMP_TO_EDGE);

        backend.bindVertexArray(0);
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, 0);
        initialized = true;
        System.out.println("[MatouRenderer] ready mesh=" + vertexCount
                + " verts stride=" + SKINNED_STRIDE
                + " texture=" + beastTex.width() + "x" + beastTex.height()
                + " program=" + program);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        render(event.partialTicks);
    }

    /**
     * Matrix capture (1614-native, measured live 2026-09-12): the fixed-
     * function stacks still hold the world camera transform while chunks
     * render, but by {@link #onRenderWorldLast} time the modelview is
     * dead (vestigial rotate, ~zero translation — every beast culls, the
     * textured draw never fires). So the matrices are read here, once per
     * chunk pass with the latest winning, and the Last draw of the same
     * frame consumes the stored pair — same pure chain downstream
     * (transpose once, multiply, seal, decide), only the read point
     * moves. The event body is never touched (no new member surface —
     * only the class rides the pin in tools/run-live.sh).
     */
    @SubscribeEvent
    public void onRenderWorldPre(RenderWorldEvent.Pre event) {
        viewMatrixBuffer.clear();
        projMatrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, viewMatrixBuffer);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projMatrixBuffer);
        for (int i = 0; i < 16; i++) {
            frameViewCol[i] = viewMatrixBuffer.get(i);
            frameProjCol[i] = projMatrixBuffer.get(i);
        }
        haveFrameMatrices = true;
    }

    public void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return;
        }
        // 1614 truth: renderViewEntity is EntityLivingBase-typed (SRG
        // field_71451_h, descriptor measured via javap on the pinned
        // vanilla primary — never recalled). Narrow to Entity by cast:
        // every member read below is declared on Entity, so Reobf hits
        // the map directly (a LivingBase-owned ref would walk off the
        // stripped stubs and pass through to die linking live).
        Entity view = (Entity) mc.renderViewEntity;
        if (view == null) {
            return;
        }

        // Owner discipline (hub decisions/LOOT.md, same as 1122 fix
        // 6988515): the world field is WorldClient-typed but
        // loadedEntityList is declared on World — read it through the
        // declaring type so Reobf maps the ref (a WorldClient-owned ref
        // walks nowhere: stubs never ship, the chain ends, the name
        // passes through and dies linking live).
        World clientWorld = mc.theWorld;
        List<Entity> list = clientWorld.loadedEntityList;
        if (list == null || list.isEmpty()) {
            return;
        }

        if (!initialized) {
            initGl();
        }

        double eyeX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double eyeY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double eyeZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        // Render-plan tranche (hub decisions/GPU_INSTANCING.md): every
        // drawn beast rides a sealed instance record through the pure
        // plan — frustum-culled records never reach the upload, never
        // silently. Model keys are mob-addressed (one bucket per mob
        // today over the single shared mesh — per-mob meshes plug into
        // the same keys), radii bound the sealed hitboxes (the cull
        // never clips a limb the hit-tester still serves).
        List<Rec> recs = new ArrayList<Rec>();
        List<MatouEntity> beasts = new ArrayList<MatouEntity>();
        for (Entity e : list) {
            if (e instanceof MatouEntity && !e.isDead) {
                MatouEntity beast = (MatouEntity) e;
                double entX = e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks;
                double entY = e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks;
                double entZ = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks;
                float radius = RenderSeal.boundRadius(beast.hitBoxes(),
                        e.posX, e.posY, e.posZ);
                recs.add(new Rec(beast.mobOrFirst(), TEXTURE_TINT,
                        entX, entY, entZ, radius, e.rotationYaw));
                beasts.add(beast);
            }
        }

        if (recs.isEmpty()) {
            return;
        }

        // No Pre seen yet this session means no rendered frame preceded
        // this draw (chunk rendering always fires Pre first) — skip the
        // frame rather than cull against a zero matrix pair.
        if (!haveFrameMatrices) {
            return;
        }
        // Same GL era as the derived path (hub
        // decisions/GPU_INSTANCING.md): fixed-function matrices arrive
        // column-major, transposed and multiplied once, purely, into
        // the row-major product the plan consumes. The pair is the
        // Pre-captured one above, never a Last-time re-read (dead on
        // 1614 — see onRenderWorldPre).
        float[] vp = ViewProjection.vpRowMajor(frameViewCol, frameProjCol);
        // The draw uniforms ride the same captured pair (a Last-time
        // buffer re-read would upload the dead modelview and misplace
        // every instance the cull just kept).
        viewMatrixBuffer.clear();
        viewMatrixBuffer.put(frameViewCol);
        viewMatrixBuffer.flip();
        projMatrixBuffer.clear();
        projMatrixBuffer.put(frameProjCol);
        projMatrixBuffer.flip();
        Map<MatouId, Object> states = RenderSeal.seal(
                RenderJob.vocabulary(),
                new double[] {eyeX, eyeY, eyeZ}, vp, recs);
        Map<String, List<Integer>> buckets = RENDER_JOB.decide(
                ForgeSnapshot.snapshot(frame++, states));

        if (buckets.isEmpty()) {
            return;
        }
        int total = 0;
        for (List<Integer> bucket : buckets.values()) {
            total += bucket.size();
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);

        backend.useProgram(program);
        backend.uniformMatrix4fv(uProjLoc, false, projMatrixBuffer);
        backend.uniformMatrix4fv(uViewLoc, false, viewMatrixBuffer);
        backend.uniform1i(uTexLoc, 0);

        backend.bindVertexArray(vao);
        // One instanced draw per planned bucket (the GPU execution
        // order InstanceBucket.plan proves): both buffers are repacked
        // per bucket, so a culled bucket costs nothing and a visible one
        // binds once. The texture binds per bucket beside the repack —
        // one shared texture today, per-mob textures plug the same call.
        // Bone deltas ride per instance (GPU skinning over the static
        // VBO — one walk phase per mob, never a shared uniform).
        MatouModel skinModel = BeastModel.cached().model();
        for (Map.Entry<String, List<Integer>> bucket
                : buckets.entrySet()) {
            instanceBuffer.clear();
            boneBuffer.clear();
            for (Integer index : bucket.getValue()) {
                MatouEntity beast = beasts.get(index.intValue());
                Entity e = beast;
                if (instanceBuffer.remaining() < InstanceFormat.FLOATS_PER_INSTANCE) {
                    FloatBuffer expanded = ByteBuffer.allocateDirect(instanceBuffer.capacity() * 2 * 4)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                    instanceBuffer.flip();
                    expanded.put(instanceBuffer);
                    instanceBuffer = expanded;
                }
                if (boneBuffer.remaining() < SKINNED_BONES * 16) {
                    FloatBuffer expanded = ByteBuffer.allocateDirect(boneBuffer.capacity() * 2 * 4)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                    boneBuffer.flip();
                    expanded.put(boneBuffer);
                    boneBuffer = expanded;
                }
                double entX = e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks;
                double entY = e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks;
                double entZ = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks;

                float yaw = (float) Math.toRadians(-e.rotationYaw);
                float pitch = (float) Math.toRadians(e.rotationPitch);
                InstanceFormat.pack(instanceBuffer,
                        entX - eyeX, entY - eyeY, entZ - eyeZ,
                        yaw, pitch, 1.0f,
                        1.0f, 0.7f, 0.7f, 1.0f,
                        0.0f, 0.0f);
                double t = e.ticksExisted / 20.0;
                double dist = BeastAnimation.interpDistMoved(
                        e.prevDistanceWalkedModified,
                        e.distanceWalkedModified, partialTicks);
                Molang.Ctx ctx = BeastAnimation.animCtx(t, dist);
                MatouAnimation.AnimPose pose = BeastAnimation.poseFor(
                        beast.mobOrFirst(), t, ctx);
                Map<String, float[]> deltas = skinModel.poseDeltaMatrices(pose);
                for (int bi = 0; bi < skinModel.bones.size(); bi++) {
                    packColumns(boneBuffer,
                            deltas.get(skinModel.bones.get(bi).name));
                }
            }
            instanceBuffer.flip();
            boneBuffer.flip();
            backend.bindTexture(GlBackend.GL_TEXTURE_2D, texture);
            backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, instanceVbo);
            backend.bufferData(GlBackend.GL_ARRAY_BUFFER, instanceBuffer, GlBackend.GL_STREAM_DRAW);
            backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, boneVbo);
            backend.bufferData(GlBackend.GL_ARRAY_BUFFER, boneBuffer, GlBackend.GL_STREAM_DRAW);
            // Draw-proof discipline (hub decisions/MATOU_MODEL.md, visual
            // tranche): pre-existing GL errors belong to the shared context
            // (MC's own state may carry some) — drain them so only this draw
            // is judged, then refuse loudly if GL rejects it. A rejected draw
            // that still logged "drew" would be a silent pass.
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            }
            backend.drawArraysInstanced(GlBackend.GL_TRIANGLES, 0, vertexCount, bucket.getValue().size());
            int glErr = GL11.glGetError();
            if (glErr != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("E_GL_DRAW:failed <" + glErr
                        + "> (instanced beast draw rejected — see hub decisions/GL_INSTANCING_ADAPTER.md)");
            }
        }
        if (!drawLogged) {
            drawLogged = true;
            System.out.println("[MatouRenderer] drew instances=" + total
                    + " mesh=" + vertexCount + " verts"
                    + " buckets=" + buckets.size());
        }

        backend.bindVertexArray(0);
        backend.useProgram(0);
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Packs one SPI row-major delta as 4 GL columns (transpose once at
     * pack — the shader builds {@code mat4} from columns, the SPI
     * contract delivers rows; verbatim values, era-native order).
     */
    private static void packColumns(FloatBuffer dst, float[] rowMajor) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                dst.put(rowMajor[r * 4 + c]);
            }
        }
    }

}
