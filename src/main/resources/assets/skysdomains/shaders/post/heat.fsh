#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform HeatConfig {
    float Strength;
    float Speed;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    float t = GameTime * Speed;
    float wave = sin(texCoord.y * 60.0 + t * 6.2831) * cos(texCoord.x * 35.0 - t * 4.0);
    vec2 offset = vec2(wave, sin(texCoord.x * 80.0 + t * 5.0)) * Strength * (1.0 - texCoord.y * 0.5);
    vec4 color = texture(InSampler, texCoord + offset);
    fragColor = vec4(color.rgb * vec3(1.03, 0.98, 0.95), 1.0);
}
