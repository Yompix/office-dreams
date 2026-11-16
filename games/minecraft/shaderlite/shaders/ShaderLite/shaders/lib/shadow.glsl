// Shadow map distortion: more texels near the player, fewer far away
#define SHADOW_DISTORT 0.85
vec2 distortShadow(vec2 p) { return p / (length(p) * SHADOW_DISTORT + (1.0 - SHADOW_DISTORT)); }
float distortFactor(vec2 p) { return length(p) * SHADOW_DISTORT + (1.0 - SHADOW_DISTORT); }
