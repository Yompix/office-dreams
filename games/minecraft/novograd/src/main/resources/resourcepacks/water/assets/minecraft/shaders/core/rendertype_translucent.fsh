#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 wpos;
in float isWater;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (isWater > 0.5) {
        float t = GameTime * 1200.0;
        float k = 0.39269908;
        vec2 p = wpos.xz;
        // рябь из нескольких волн и солнечные блики на гребнях
        float wv = sin(p.x * k * 4.0 + t * 1.3) * sin(p.y * k * 3.0 - t * 1.1)
                 + 0.5 * sin((p.x + p.y) * k * 7.0 + t * 2.3)
                 + 0.25 * sin((p.x - p.y) * k * 13.0 - t * 3.1);
        float light = dot(vertexColor.rgb, vec3(0.299, 0.587, 0.114));
        float spec = pow(clamp(wv * 0.5, 0.0, 1.0), 5.0) * 0.55 * light;
        // глубокий бирюзовый цвет, светлее на гребнях
        vec3 deep = vec3(0.04, 0.22, 0.32) * (0.4 + light);
        color.rgb = mix(color.rgb, deep, 0.35) * (0.92 + wv * 0.06) + vec3(spec * 0.9, spec, spec);
        // ближе — прозрачнее, дальше — плотнее (как отражение неба под углом)
        color.a = mix(0.55, 0.9, smoothstep(6.0, 60.0, vertexDistance));
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
