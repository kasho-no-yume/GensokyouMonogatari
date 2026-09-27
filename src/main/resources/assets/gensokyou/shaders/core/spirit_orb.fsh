#version 150

uniform vec4 Tint;
uniform float Fill;
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec3 viewNormal;
in vec3 viewPos;
in vec2 scrollCoord;

out vec4 fragColor;

/* Qi FIELD, not a ball. Three deliberate departures from the original shell:
     1. fresnel exponent 2.2 -> 1.2, so density falls off slowly along the view ray and
        the outer edge has no recognisable silhouette;
     2. a faint interior term `Fill` (0 at the silhouette, strongest looking straight
        through the middle) so the body is neither fully transparent nor solid;
     3. the shell term is scaled down, because it now only supplies the rim sheen.
   Additive blending (SRC_ALPHA, ONE) => rgb is NOT premultiplied by a; all density is
   expressed through alpha. */
void main() {
    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    float ndv = abs(dot(N, V));
    float rim = pow(1.0 - ndv, 1.2);
    /* Two noise layers at different drift rates (the second scaled 1.7x relative to
       scrollCoord, which already carries the Time factor) produce the wisps. */
    float n1 = texture(Sampler0, scrollCoord).r;
    float n2 = texture(Sampler0, scrollCoord * 1.7 + 0.31).r;
    float noise = clamp(n1 * 1.4 * n2 * 1.4 + 0.12, 0.0, 1.0);
    float shell = rim * rim * 0.9 + rim * 0.35;
    float fill = Fill * (0.5 + 0.5 * ndv);
    float a = clamp(shell * 0.85 + fill + 0.05 * ndv * noise, 0.0, 1.0)
            * (0.35 + 0.75 * noise);
    vec3 rgb = Tint.rgb * (0.55 + 1.1 * rim) * vertexColor.rgb;
    fragColor = vec4(rgb, a * vertexColor.a);
}
