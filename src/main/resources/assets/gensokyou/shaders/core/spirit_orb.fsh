#version 150

uniform vec4 Tint;
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec3 viewNormal;
in vec3 viewPos;
in vec2 scrollCoord;

out vec4 fragColor;

/* fresnel 边缘光 × 雾噪声 = "气"：中心近透、边缘亮、表面流动。
   加法混合 (SRC_ALPHA, ONE) 下 rgb 不再预乘 a，密度全部由 alpha 表达。 */
void main() {
    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = abs(dot(N, V));
    float rim = pow(1.0 - ndv, 2.2);
    /* 双层异速噪声（第二层乘 1.7 = 相对漂移，scrollCoord 已含 Time 因子）出丝缕感 */
    float n1 = texture(Sampler0, scrollCoord).r;
    float n2 = texture(Sampler0, scrollCoord * 1.7 + 0.31).r;
    float noise = clamp(n1 * 1.4 * n2 * 1.4 + 0.12, 0.0, 1.0);
    float shell = rim * rim * 0.9 + rim * 0.35;
    float a = clamp(shell + 0.05 * ndv * noise, 0.0, 1.0) * (0.35 + 0.75 * noise);
    vec3 rgb = Tint.rgb * (0.55 + 1.1 * rim) * vertexColor.rgb;
    fragColor = vec4(rgb, a * vertexColor.a * 0.8);
}
