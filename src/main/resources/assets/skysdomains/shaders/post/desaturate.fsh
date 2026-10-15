#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform DesaturateConfig {
    float Amount;
    float Darken;
};

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 grey = vec3(luma) * (1.0 - Darken);
    fragColor = vec4(mix(color.rgb, grey, Amount), 1.0);
}
