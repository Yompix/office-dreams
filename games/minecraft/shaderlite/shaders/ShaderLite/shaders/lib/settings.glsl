// ShaderLite settings. Defaults = LOW profile.

// --- Shadows ---
#define SHADOWS                 // Sun/moon shadows
#define SHADOW_FILTER 1 // [1 4 8]
//#define SHADOW_ENTITIES       // Shadows from mobs, players, chests
const int shadowMapResolution = 512;     // [512 768 1024 1536 2048]
const float shadowDistance = 48.0;       // [32.0 48.0 64.0 80.0 96.0 128.0 160.0]
const float shadowDistanceRenderMul = 1.0; // [-1.0 1.0]
const float sunPathRotation = -25.0;     // [-40.0 -30.0 -25.0 -20.0 -10.0 0.0 10.0 20.0 30.0]
const bool shadowHardwareFiltering = true;
const float shadowIntervalSize = 4.0;
const float ambientOcclusionLevel = 1.0;
const float wetnessHalflife = 300.0;
const float drynessHalflife = 100.0;
const float eyeBrightnessHalflife = 4.0;

// --- Light ---
#define LIGHT_SCALE 50 // [50 100]
#define SUN_BRIGHTNESS 1.0      // [0.6 0.8 1.0 1.2 1.4]
#define AMBIENT_BRIGHTNESS 1.0  // [0.6 0.8 1.0 1.2 1.4]
#define TORCH_BRIGHTNESS 1.0    // [0.6 0.8 1.0 1.2 1.4 1.6]
#define MIN_LIGHT 1.0 // [0.0 0.5 1.0 2.0 4.0]

// --- World ---
#define WAVING_PLANTS           // Waving leaves and grass
#define WAVING_SPEED 1.0        // [0.5 0.75 1.0 1.5 2.0]
#define WATER_WAVES 2 // [0 2 4]
#define WATER_ALPHA 0.65        // [0.4 0.5 0.65 0.8 0.9]
#define FOG_DENSITY 1.0         // [0.0 0.5 1.0 1.5 2.0]
//#define GODRAYS               // Cheap sun rays (1/4 resolution)

// --- Image ---
#define BLOOM                   // Glow around bright things (1/4 resolution)
#define BLOOM_STRENGTH 0.6      // [0.2 0.4 0.6 0.8 1.0 1.5]
#define EXPOSURE 1.0            // [0.6 0.8 1.0 1.2 1.4 1.6]
#define SATURATION 1.10         // [0.8 0.9 1.0 1.1 1.2 1.3]
#define CONTRAST 1.05           // [0.9 1.0 1.05 1.1 1.2]
#define WARMTH 0.10             // [-0.2 -0.1 0.0 0.05 0.10 0.15 0.2]
#define SHARPEN 40 // [0 20 40 60 80 100]
#define VIGNETTE 0.25           // [0.0 0.15 0.25 0.4]

#ifdef SHADOWS
#endif
#ifdef SHADOW_ENTITIES
#endif
#ifdef GODRAYS
#endif
#ifdef BLOOM
#endif
