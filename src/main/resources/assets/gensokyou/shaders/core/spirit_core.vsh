#version 150

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Time;

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec4 vertexColor;
out vec3 viewNormal;
out vec3 viewPos;
out vec2 scrollCoord;

/* Focus core: same unit-sphere vertex layout as spirit_orb (origin-centred sphere,
   normal = normalize(Position), UV = spherical parameterisation used only as a
   two-axis noise coordinate). Kept byte-identical to spirit_orb.vsh on purpose so the
   two shaders stay trivially comparable. */
void main() {
    vec4 mv = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * mv;
    viewNormal = normalize(mat3(ModelViewMat) * normalize(Position));
    viewPos = mv.xyz;
    vertexColor = Color;
    scrollCoord = UV0 + vec2(Time * 0.008, Time * 0.013);
}
