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
out vec3 wpos;
out float isWater;

void main() {
    vec3 pos = Position + ChunkOffset;
    // Новоград: вода — та, что окрашена синим цветом биома. Поверхность мягко волнуется.
    isWater = (Color.b > Color.r + 0.08 && Color.b > Color.g) ? 1.0 : 0.0;
    wpos = Position;
    if (isWater > 0.5) {
        float t = GameTime * 1200.0;
        float k = 0.39269908;
        float f = fract(Position.y);
        if (f > 0.75 || f < 0.01) {
            pos.y += (sin(t * 1.4 + Position.x * k * 2.0 + Position.z * k) * 0.6 + sin(t * 2.1 + Position.z * k * 3.0) * 0.4) * 0.045 - 0.03;
        }
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
