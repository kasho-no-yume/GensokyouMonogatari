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

/* 灵气球（ritual-fx-overhaul D6）：单位球心在原点，法线 = normalize(Position)。
   UV 为球面参数化坐标，双轴漂移采样雾噪声出"气"的流动。 */
void main() {
    vec4 mv = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * mv;
    viewNormal = normalize(mat3(ModelViewMat) * normalize(Position));
    viewPos = mv.xyz;
    vertexColor = Color;
    scrollCoord = UV0 + vec2(Time * 0.008, Time * 0.013);
}
