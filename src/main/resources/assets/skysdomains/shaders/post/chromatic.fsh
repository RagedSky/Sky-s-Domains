#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform ChromaticConfig {
    float Offset;
};

void main() {
    vec2 dir = texCoord - vec2(0.5);
    float r = texture(InSampler, texCoord + dir * Offset).r;
    float g = texture(InSampler, texCoord).g;
    float b = texture(InSampler, texCoord - dir * Offset).b;
    fragColor = vec4(r, g, b, 1.0);
}
