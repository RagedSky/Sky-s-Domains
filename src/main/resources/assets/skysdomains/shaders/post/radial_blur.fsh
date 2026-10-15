#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform RadialConfig {
    float Strength;
    float Samples;
};

void main() {
    vec2 dir = texCoord - vec2(0.5);
    vec3 sum = vec3(0.0);
    int samples = int(Samples);
    for (int i = 0; i < samples; i++) {
        float t = float(i) / float(samples);
        sum += texture(InSampler, texCoord - dir * t * Strength).rgb;
    }
    fragColor = vec4(sum / float(samples), 1.0);
}
