#version 150

uniform vec4 Tint;
uniform float Density;
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec3 viewNormal;
in vec3 viewPos;
in vec2 scrollCoord;

out vec4 fragColor;

/* Focus core = an OPAQUE green nucleus, as opposed to spirit_orb's wispy qi field.
   The whole difference from spirit_orb.fsh is the density curve: here the interior sits
   at `Density` (never transparent) and the fresnel rim only ADDS on top, so the ball
   reads as a solid thing that occludes what is behind it. Rendered with alpha blending
   (SRC_ALPHA, ONE_MINUS_SRC_ALPHA), NOT additive -- additive "opacity" is impossible. */
void main() {
    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = abs(dot(N, V));
    /* Rim exponent is higher than spirit_orb's on purpose: a solid ball wants a tight
       edge highlight, a field wants a wide slow falloff. */
    float rim = pow(1.0 - ndv, 2.6);
    /* Two noise layers at different drift rates: internal wisps, so the interior is not
       a flat disc of colour. Sampler0 is the shared soft mist texture (REPEAT). */
    float n1 = texture(Sampler0, scrollCoord).r;
    float n2 = texture(Sampler0, scrollCoord * 1.9 + 0.37).r;
    float noise = clamp(n1 * 1.3 * n2 * 1.3 + 0.18, 0.0, 1.0);
    float base = clamp(Density, 0.0, 1.0);
    float density = clamp(base + (1.0 - base) * (rim * 1.15 + 0.25 * noise), 0.0, 1.0);
    vec3 rgb = Tint.rgb * (0.72 + 0.5 * rim) * vertexColor.rgb;
    fragColor = vec4(rgb, density * vertexColor.a);
}
