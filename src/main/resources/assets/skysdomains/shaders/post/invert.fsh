#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform InvertConfig {
    float InverseAmount;
};

void main() {
    vec4 color = texture(InSampler, texCoord);
    fragColor = vec4(mix(color.rgb, 1.0 - color.rgb, InverseAmount), 1.0);
}
