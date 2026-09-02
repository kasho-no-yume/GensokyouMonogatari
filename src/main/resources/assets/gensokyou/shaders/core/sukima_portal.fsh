#version 150

#moj_import <matrix.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float GameTime;
uniform int EndPortalLayers;

in vec4 texProj0;

/* 隙间黑红调色板：层叠眼由暗到微亮（红主导，绿蓝压暗）。
 * 改动此处需与 EndPortalLayers（sukima_portal.json，当前 6）保持匹配。 */
const vec3[] COLORS = vec3[](
    vec3(0.035, 0.005, 0.007),
    vec3(0.055, 0.008, 0.010),
    vec3(0.080, 0.012, 0.015),
    vec3(0.110, 0.017, 0.021),
    vec3(0.145, 0.023, 0.028),
    vec3(0.185, 0.030, 0.036)
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

/* 缩放 2.35→1.1（原版 8.5→1.0）：平铺密度降约一个数量级，眼更少更大 */
mat4 void_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );

    mat2 rotate = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));

    mat2 scale = mat2(2.6 - layer * 0.25);

    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

out vec4 fragColor;

void main() {
    /* 底色：自有纹理略暗并压绿蓝 → 偏黑的黑红背景 */
    vec3 color = textureProj(Sampler0, texProj0).rgb * vec3(0.6, 0.32, 0.32);
    for (int i = 0; i < EndPortalLayers; i++) {
        color += textureProj(Sampler1, texProj0 * void_layer(float(i + 1))).rgb * COLORS[i];
    }
    fragColor = vec4(color, 1.0);
}
