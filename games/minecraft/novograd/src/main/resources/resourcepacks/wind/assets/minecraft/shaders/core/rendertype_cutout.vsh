#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform float GameTime;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ChunkOffset;
    // Новоград: ветер. Качаются только окрашенные биомом (листва, трава, лианы); волна привязана к блокам
    // и непрерывна на стыках секций (частоты кратны 2pi/16).
    bool tinted = abs(Color.r - Color.g) > 0.03 || abs(Color.g - Color.b) > 0.03;
    if (tinted) {
        float t = GameTime * 1200.0;
        float k = 0.39269908;
        vec3 p = Position;
        float gust = 0.65 + 0.35 * sin(t * 0.21 + (p.x + p.z) * k);
        float w = sin(t * 1.6 + p.x * k * 2.0 + p.z * k) * 0.55 + sin(t * 2.7 + p.z * k * 3.0 + p.y * k * 2.0) * 0.3 + sin(t * 4.3 + p.x * k * 5.0) * 0.15;
        float amp = 0.10 * clamp(fract(Position.y) * 1.15, 0.0, 1.0);
        pos.x += w * amp * gust;
        pos.z += w * amp * gust * 0.6;
        pos.y += sin(t * 2.2 + p.x * k * 3.0) * amp * 0.25;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
