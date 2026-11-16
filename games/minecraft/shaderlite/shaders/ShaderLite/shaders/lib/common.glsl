// Shared functions: sky, light colors, fog. Include after settings.glsl.
#define PI 3.14159265

uniform vec3 sunPosition;
uniform vec3 upPosition;
uniform vec3 shadowLightPosition;
uniform float rainStrength;
uniform float frameTimeCounter;
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;
uniform float nightVision;
uniform float blindness;
uniform vec3 fogColor;
uniform vec3 skyColor;
uniform float far;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 toLinear(vec3 c) { return c * c; }
float sat(float x) { return clamp(x, 0.0, 1.0); }

// Sun elevation above the horizon (-1..1) and derived factors
float sunElevation() { return dot(normalize(sunPosition), normalize(upPosition)); }
float dayFactor() { return smoothstep(-0.10, 0.12, sunElevation()); }
float sunsetFactor() { float e = sunElevation(); return sat(1.0 - abs(e - 0.02) / 0.22); }

vec3 sunLightColor() {
#if defined NETHER || defined END
    return vec3(0.0);
#else
    float e = sunElevation();
    vec3 noon = vec3(1.00, 0.93, 0.82) * 2.6;
    vec3 sunset = vec3(1.00, 0.48, 0.20) * 1.8;
    vec3 day = mix(sunset, noon, smoothstep(0.02, 0.35, e));
    vec3 moon = vec3(0.45, 0.55, 0.85) * 0.22;
    vec3 c = e > -0.04 ? day * smoothstep(-0.04, 0.06, e) : moon * smoothstep(0.04, 0.14, -e);
    return c * (1.0 - rainStrength * 0.85) * SUN_BRIGHTNESS;
#endif
}

vec3 skyGradient(vec3 dir) {
    vec3 up = normalize(upPosition);
    vec3 sun = normalize(sunPosition);
    float h = dot(dir, up);
    float d = dayFactor(), s = sunsetFactor();
    vec3 zenith = mix(vec3(0.010, 0.014, 0.035), vec3(0.11, 0.28, 0.78), d);
    vec3 horizon = mix(vec3(0.030, 0.040, 0.080), vec3(0.46, 0.62, 0.88), d);
    float toward = dot(dir, sun) * 0.5 + 0.5;
    vec3 sunsetHor = mix(vec3(0.55, 0.30, 0.45), vec3(1.15, 0.50, 0.18), toward * toward);
    horizon = mix(horizon, sunsetHor, s * 0.85);
    zenith = mix(zenith, vec3(0.16, 0.20, 0.42), s * 0.45);
    float t = pow(sat(h), 0.45);
    vec3 col = mix(horizon, zenith, t);
    col *= mix(1.0, 0.55, sat(-h * 3.0));
    // glow around the sun
    float mu = max(dot(dir, sun), 0.0);
    col += sunLightColor() * (pow(mu, 12.0) * 0.18 + pow(mu, 180.0) * 0.6) * (0.4 + s);
    // rain: grey sky
    vec3 grey = vec3(luma(col)) * vec3(0.9, 0.95, 1.0);
    col = mix(col, grey * 0.7, rainStrength * 0.85);
    return col;
}

vec3 ambientColor() {
#if defined NETHER
    return toLinear(fogColor) * 0.9 + 0.06;
#elif defined END
    return vec3(0.16, 0.12, 0.22);
#else
    float d = dayFactor();
    vec3 a = mix(vec3(0.030, 0.040, 0.075), vec3(0.42, 0.52, 0.70), d);
    a = mix(a, vec3(0.45, 0.36, 0.40), sunsetFactor() * 0.5);
    return a * (1.0 - rainStrength * 0.4) * AMBIENT_BRIGHTNESS;
#endif
}

vec3 torchColor() { return vec3(1.00, 0.62, 0.32) * 1.6 * TORCH_BRIGHTNESS; }

// Fog color: sky near the horizon in the view direction (aerial perspective)
vec3 fogColorFor(vec3 dir) {
#if defined NETHER || defined END
    return toLinear(fogColor);
#else
    vec3 up = normalize(upPosition);
    vec3 hdir = normalize(dir - up * dot(dir, up) * 0.85);
    return skyGradient(hdir);
#endif
}

// Dither noise (interleaved gradient noise)
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }
