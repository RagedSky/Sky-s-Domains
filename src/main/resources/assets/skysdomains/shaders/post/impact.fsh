#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Anime impact frame: the real scene is reduced to two tones with ink edges and radial speed lines.
layout(std140) uniform ImpactConfig {
    float Threshold;   // luma threshold between the two tones
    float Invert;      // 0 = black ink on white, 1 = white ink on black
    float Lines;       // density of the radial speed lines (0 = none)
    float Halftone;    // strength of the halftone dots in the dark tone
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float luma(vec2 uv) {
    vec3 c = texture(InSampler, uv).rgb;
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float hash(float n) {
    return fract(sin(n * 127.1 + 311.7) * 43758.5453);
}

void main() {
    vec2 px = 1.0 / InSize;
    float l = luma(texCoord);

    // Sobel edges
    float tl = luma(texCoord + px * vec2(-1.0, -1.0));
    float t  = luma(texCoord + px * vec2( 0.0, -1.0));
    float tr = luma(texCoord + px * vec2( 1.0, -1.0));
    float ml = luma(texCoord + px * vec2(-1.0,  0.0));
    float mr = luma(texCoord + px * vec2( 1.0,  0.0));
    float bl = luma(texCoord + px * vec2(-1.0,  1.0));
    float b  = luma(texCoord + px * vec2( 0.0,  1.0));
    float br = luma(texCoord + px * vec2( 1.0,  1.0));
    float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
    float edge = clamp(length(vec2(gx, gy)) * 2.5, 0.0, 1.0);

    // Two tones with a soft knee, plus halftone dots in the dark tone
    float tone = smoothstep(Threshold - 0.08, Threshold + 0.08, l);
    vec2 cell = texCoord * InSize / 9.0;
    float dot = 1.0 - smoothstep(0.25, 0.45, length(fract(cell) - 0.5));
    tone = max(tone, dot * Halftone * (1.0 - tone) * step(0.25, l));

    // Radial speed lines that flicker every few frames
    vec2 d = (texCoord - 0.5) * vec2(InSize.x / InSize.y, 1.0);
    float r = length(d);
    float ang = atan(d.y, d.x);
    float seed = floor(GameTime * 2400.0);
    float lineCount = 180.0 * Lines;
    float ray = step(0.72, hash(floor(ang / 6.2831853 * lineCount) + seed));
    float speed = ray * smoothstep(0.22, 0.6, r) * step(0.0, Lines);

    float ink = max(edge, speed);
    float value = mix(tone, 0.0, ink);
    value = mix(value, 1.0 - value, Invert);
    fragColor = vec4(vec3(value), 1.0);
}
