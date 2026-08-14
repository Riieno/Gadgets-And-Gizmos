#version 150

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

uniform sampler2D DepthSampler;
uniform vec2 ScreenSize;
uniform mat4 InvViewProjMat;
uniform vec4 MarkerCenterRadius;
uniform vec4 HighlightColor;

out vec4 fragColor;

// Rebuild a world position from the sampled depth
vec3 reconstructWorld(vec2 uv, float ndcZ) {
    vec4 clip = vec4(uv * 2.0 - 1.0, ndcZ, 1.0);
    vec4 world = InvViewProjMat * clip;
    if (abs(world.w) < 0.001) {
        return vec3(1e9);
    }
    return world.xyz / world.w;
}

// Draw the depth tested radius highlight
void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(DepthSampler, uv).r;

    if (depth >= 1.0) {
        discard;
    }

    vec3 worldPos = reconstructWorld(uv, depth * 2.0 - 1.0);
    float dist = distance(worldPos, MarkerCenterRadius.xyz);
    float radius = MarkerCenterRadius.w;

    float feather = max(radius * 0.18, 0.08);
    float ringThickness = max(radius * 0.18, 0.12);
    float outerMask = 1.0 - smoothstep(radius - feather, radius, dist);
    float innerMask = 1.0 - smoothstep(max(radius - ringThickness - feather, 0.0), max(radius - ringThickness, 0.0), dist);
    float ringMask = clamp(outerMask - innerMask, 0.0, 1.0);
    float fillMask = 1.0 - smoothstep(radius, radius + feather, dist);
    float alpha = max(ringMask, fillMask * 0.18) * HighlightColor.a;

    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(HighlightColor.rgb, alpha);
}
