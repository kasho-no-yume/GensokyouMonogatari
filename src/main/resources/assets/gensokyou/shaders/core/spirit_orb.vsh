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

/* Qi field (ritual-fx-overhaul D6): unit sphere centred on the origin, normal =
   normalize(Position). UV is the spherical parameterisation, used only as a two-axis
   coordinate for scrolling mist noise. */
void main() {
    vec4 mv = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * mv;
    viewNormal = normalize(mat3(ModelViewMat) * normalize(Position));
    viewPos = mv.xyz;
    vertexColor = Color;
    scrollCoord = UV0 + vec2(Time * 0.008, Time * 0.013);
}
