package com.ps1bedroom.room.engine

// GLSL ES 3.00 ports of the HTML's shaders:
//  - PS1 surface shader = three.js r128 MeshPhongMaterial (specular 0, physicallyCorrectLights off) + makePS1Material's
//    vertex snap / affine warp / macro variation, with the same light maths, BasicShadowMap test and FogExp2
//  - post pass = postMaterial (AO, window light rays, fan glow, grade, vignette, Bayer dither + RGB555)

object Shaders {
    const val MAX_POINT = 12
    const val MAX_SPOT = 4
    const val MAX_HEMI = 2

    const val PS1_VERT = """#version 300 es
precision highp float;
layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec2 uv;
uniform mat4 modelMatrix;
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
uniform mat3 normalMatrix;
uniform mat3 uvTransform;
uniform vec2 uJitterGrid;
uniform float uAffineStrength;
uniform int uPs1;
uniform mat4 uShadowMatrix0;
uniform mat4 uShadowMatrix1;
out vec3 vNormal;
out vec3 vViewPosition;
out vec2 vUv;
out float vAffineW;
out vec2 vAffineUv;
out vec3 vMacroPos;
out float vFogDepth;
out vec4 vShadowCoord0;
out vec4 vShadowCoord1;
void main() {
  vUv = (uvTransform * vec3(uv, 1.0)).xy;
  vec3 transformedNormal = normalMatrix * normal;
  vNormal = normalize(transformedNormal);
  vec4 worldPosition = modelMatrix * vec4(position, 1.0);
  vMacroPos = worldPosition.xyz;
  vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
  gl_Position = projectionMatrix * mvPosition;
  if (uPs1 == 1 && gl_Position.w > 0.0) {
    // PS1 GTE screen-space vertex snap. Up close the grid is as coarse as ever; beyond 2 m it gets finer with
    // distance so small far things don't collapse. The snapped vertex is slid back onto its own surface plane
    // along the view ray, so layered surfaces keep their real depth order (no flicker).
    vec2 snapGrid = uJitterGrid * max(1.0, gl_Position.w * 0.5);
    vec2 snapNdc = floor(snapGrid * (gl_Position.xy / gl_Position.w) + 0.5) / snapGrid;
    vec3 snapDir = vec3((snapNdc.x + projectionMatrix[2][0]) / projectionMatrix[0][0],
                        (snapNdc.y + projectionMatrix[2][1]) / projectionMatrix[1][1], -1.0);
    vec3 snapN = normalize(transformedNormal);
    float snapDen = dot(snapDir, snapN);
    float snapT = snapDen != 0.0 ? dot(mvPosition.xyz, snapN) / snapDen : -1.0;
    if (abs(snapDen) > 0.02 && snapT > 0.0 && abs(snapT + mvPosition.z) < 0.25 * abs(mvPosition.z) + 0.05) {
      gl_Position = projectionMatrix * vec4(snapDir * snapT, 1.0);
    } else {
      vec4 snapped = gl_Position;
      snapped.xy = snapNdc * gl_Position.w;
      gl_Position = snapped;
    }
  }
  float strength = uPs1 == 1 ? uAffineStrength : 0.0;
  vAffineW = mix(1.0, max(0.22, gl_Position.w), strength);
  vAffineUv = vUv * vAffineW;
  vViewPosition = -mvPosition.xyz;
  vFogDepth = -mvPosition.z;
  vShadowCoord0 = uShadowMatrix0 * worldPosition;
  vShadowCoord1 = uShadowMatrix1 * worldPosition;
}
"""

    const val PS1_FRAG = """#version 300 es
precision highp float;
precision highp int;
#define MAX_POINT 12
#define MAX_SPOT 4
#define MAX_HEMI 2
in vec3 vNormal;
in vec3 vViewPosition;
in vec2 vUv;
in float vAffineW;
in vec2 vAffineUv;
in vec3 vMacroPos;
in float vFogDepth;
in vec4 vShadowCoord0;
in vec4 vShadowCoord1;
out vec4 fragColor;
uniform vec3 diffuse;
uniform float opacity;
uniform vec3 emissive;
uniform int uHasMap;
uniform int uHasEmissiveMap;
uniform int uMacro;
uniform int uLit;
uniform int uFog;
uniform int uDoubleSide;
uniform int uReceiveShadow;
uniform sampler2D map;
uniform sampler2D emissiveMap;
uniform vec3 ambientLightColor;
uniform int uHemiCount;
uniform vec3 uHemiSky[MAX_HEMI];
uniform vec3 uHemiGround[MAX_HEMI];
uniform vec3 uHemiDir[MAX_HEMI];
uniform int uPointCount;
uniform vec3 uPointPos[MAX_POINT];
uniform vec3 uPointColor[MAX_POINT];
uniform vec2 uPointDistDecay[MAX_POINT];
uniform int uSpotCount;
uniform vec3 uSpotPos[MAX_SPOT];
uniform vec3 uSpotDir[MAX_SPOT];
uniform vec3 uSpotColor[MAX_SPOT];
uniform vec4 uSpotParams[MAX_SPOT];   // coneCos, penumbraCos, distance, decay
uniform int uSpotShadow[MAX_SPOT];    // -1, 0 or 1 (which shadow map)
uniform sampler2D uShadowMap0;
uniform sampler2D uShadowMap1;
uniform float uShadowBias0;
uniform float uShadowBias1;
uniform vec3 fogColor;
uniform float fogDensity;

float macroHash(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
float macroNoise(vec3 p) {
  vec3 i = floor(p); vec3 f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(macroHash(i), macroHash(i + vec3(1.0, 0.0, 0.0)), f.x), mix(macroHash(i + vec3(0.0, 1.0, 0.0)), macroHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
             mix(mix(macroHash(i + vec3(0.0, 0.0, 1.0)), macroHash(i + vec3(1.0, 0.0, 1.0)), f.x), mix(macroHash(i + vec3(0.0, 1.0, 1.0)), macroHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}
// three.js punctualLightIntensityToIrradianceFactor (non-physical)
float distanceFactor(float d, float cutoff, float decay) {
  if (cutoff > 0.0 && decay > 0.0) return pow(clamp(-d / cutoff + 1.0, 0.0, 1.0), decay);
  return 1.0;
}
// BasicShadowMap: one depth comparison
float shadowAt(sampler2D sm, vec4 coord, float bias) {
  vec3 c = coord.xyz / coord.w;
  c.z += bias;
  bool inFrustum = c.x >= 0.0 && c.x <= 1.0 && c.y >= 0.0 && c.y <= 1.0 && c.z <= 1.0;
  if (!inFrustum) return 1.0;
  return step(c.z, texture(sm, c.xy).r);
}
void main() {
  vec4 diffuseColor = vec4(diffuse, opacity);
  vec2 affineUv = vAffineUv / vAffineW;
  if (uHasMap == 1) diffuseColor *= texture(map, affineUv);
  if (uMacro == 1) {
    float macroV = macroNoise(vMacroPos * 0.85) * 0.6 + macroNoise(vMacroPos * 2.9 + 7.0) * 0.4;
    diffuseColor.rgb *= 0.85 + 0.3 * macroV;
  }
  vec3 totalEmissiveRadiance = emissive;
  if (uHasEmissiveMap == 1) totalEmissiveRadiance *= (uHasMap == 1 ? texture(emissiveMap, affineUv) : texture(emissiveMap, vUv)).rgb;
  vec3 outgoing;
  if (uLit == 1) {
    vec3 N = normalize(vNormal);
    if (uDoubleSide == 1 && !gl_FrontFacing) N = -N;
    vec3 geoPos = -vViewPosition;
    vec3 direct = vec3(0.0);
    for (int i = 0; i < MAX_POINT; i++) {
      if (i >= uPointCount) break;
      vec3 lv = uPointPos[i] - geoPos;
      float ld = length(lv);
      vec3 L = lv / max(ld, 0.00001);
      vec3 col = uPointColor[i] * distanceFactor(ld, uPointDistDecay[i].x, uPointDistDecay[i].y);
      direct += max(dot(N, L), 0.0) * col;
    }
    for (int i = 0; i < MAX_SPOT; i++) {
      if (i >= uSpotCount) break;
      vec3 lv = uSpotPos[i] - geoPos;
      float ld = length(lv);
      vec3 L = lv / max(ld, 0.00001);
      float angleCos = dot(L, uSpotDir[i]);
      if (angleCos > uSpotParams[i].x) {
        float spotEffect = smoothstep(uSpotParams[i].x, uSpotParams[i].y, angleCos);
        vec3 col = uSpotColor[i] * spotEffect * distanceFactor(ld, uSpotParams[i].z, uSpotParams[i].w);
        if (uReceiveShadow == 1) {
          if (uSpotShadow[i] == 0) col *= shadowAt(uShadowMap0, vShadowCoord0, uShadowBias0);
          else if (uSpotShadow[i] == 1) col *= shadowAt(uShadowMap1, vShadowCoord1, uShadowBias1);
        }
        direct += max(dot(N, L), 0.0) * col;
      }
    }
    vec3 irradiance = ambientLightColor;
    for (int i = 0; i < MAX_HEMI; i++) {
      if (i >= uHemiCount) break;
      float w = 0.5 * dot(N, uHemiDir[i]) + 0.5;
      irradiance += mix(uHemiGround[i], uHemiSky[i], w);
    }
    outgoing = direct * diffuseColor.rgb + irradiance * diffuseColor.rgb + totalEmissiveRadiance;
  } else {
    outgoing = diffuseColor.rgb;
  }
  if (uFog == 1) {
    float fogFactor = 1.0 - exp(-fogDensity * fogDensity * vFogDepth * vFogDepth);
    outgoing = mix(outgoing, fogColor, fogFactor);
  }
  fragColor = vec4(outgoing, diffuseColor.a);
}
"""

    // Shadow maps: plain depth from the light (three's MeshDepthMaterial has no PS1 snap either)
    const val DEPTH_VERT = """#version 300 es
layout(location = 0) in vec3 position;
uniform mat4 mvp;
void main() { gl_Position = mvp * vec4(position, 1.0); }
"""
    const val DEPTH_FRAG = """#version 300 es
precision mediump float;
out vec4 fragColor;
void main() { fragColor = vec4(1.0); }
"""

    const val QUAD_VERT = """#version 300 es
layout(location = 0) in vec2 position;
out vec2 vUv;
void main() { vUv = position * 0.5 + 0.5; gl_Position = vec4(position, 0.0, 1.0); }
"""
    const val BLIT_FRAG = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D tex;
out vec4 fragColor;
void main() { fragColor = texture(tex, vUv); }
"""

    const val POST_FRAG = """#version 300 es
precision highp float;
precision highp int;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D tDiffuse;
uniform sampler2D tDepth;
uniform sampler2D tShadowMap;
uniform float uHasDepthTex;
uniform vec2 uResolution;
uniform float uDitherStrength;
uniform float uVolumetricStrength;
uniform mat4 uInvProjMatrix;
uniform mat4 uInvViewMatrix;
uniform mat4 uShadowMatrix;
uniform vec3 uCameraPos;
uniform vec3 uLightPos;
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform float uLightCosAngle;
uniform float uLightPenumbraCos;
uniform float uFanLightOn;
uniform vec3 uFanLightPos;
uniform float uTime;
uniform vec3 uGradeTint;
uniform float uGradeSat;
uniform float uGradeContrast;
uniform float uVignette;
uniform float uAOStrength;
uniform int uMarchSteps;
float viewZAt(vec2 uv) {
  float d = texture(tDepth, uv).r;
  vec4 v = uInvProjMatrix * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
  return v.z / max(0.0001, v.w);
}
float bayer4x4(vec2 pos) {
  int x = int(mod(pos.x, 4.0));
  int y = int(mod(pos.y, 4.0));
  int index = x + y * 4;
  float m = 0.0;
  if (index == 0) m = -4.0; else if (index == 1) m = 0.0; else if (index == 2) m = -3.0; else if (index == 3) m = 1.0;
  else if (index == 4) m = 2.0; else if (index == 5) m = -2.0; else if (index == 6) m = 3.0; else if (index == 7) m = -1.0;
  else if (index == 8) m = -3.0; else if (index == 9) m = 1.0; else if (index == 10) m = -4.0; else if (index == 11) m = 0.0;
  else if (index == 12) m = 3.0; else if (index == 13) m = -1.0; else if (index == 14) m = 2.0; else if (index == 15) m = -2.0;
  return m / 255.0;
}
void main() {
  vec4 baseCol = texture(tDiffuse, vUv);
  vec2 pix = floor(vUv * uResolution);
  float rawDepth = uHasDepthTex > 0.5 ? texture(tDepth, vUv).r : 0.985;
  vec4 clipPos = vec4(vUv * 2.0 - 1.0, rawDepth * 2.0 - 1.0, 1.0);
  vec4 viewPos = uInvProjMatrix * clipPos;
  viewPos /= max(0.0001, viewPos.w);
  vec4 worldPos4 = uInvViewMatrix * viewPos;
  vec3 surfacePos = worldPos4.xyz;

  if (uAOStrength > 0.001 && uHasDepthTex > 0.5) {
    float z0 = viewPos.z;
    float dist = -z0;
    if (dist < 14.0) {
      float radiusPx = clamp(26.0 / dist, 2.0, 14.0) * (uResolution.y / 240.0);
      float ang = fract(sin(dot(pix, vec2(41.3, 289.1))) * 43758.5453) * 6.2831;
      float occ = 0.0;
      for (int i = 0; i < 8; i++) {
        float a = ang + float(i) * 2.39996;
        float rr = radiusPx * (0.35 + 0.65 * float(i) / 7.0);
        vec2 suv = clamp(vUv + vec2(cos(a), sin(a)) * rr / uResolution, vec2(0.001), vec2(0.999));
        float diff = viewZAt(suv) - z0;
        occ += smoothstep(0.03, 0.22, diff) * (1.0 - smoothstep(0.5, 1.2, diff));
      }
      baseCol.rgb *= 1.0 - uAOStrength * 0.7 * (occ / 8.0);
    }
  }

  vec3 volumetricLight = vec3(0.0);
  if (uVolumetricStrength > 0.001) {
    vec3 rayVec = surfacePos - uCameraPos;
    float maxRayDist = min(length(rayVec), 9.0);
    vec3 rayDir = rayVec / max(0.0001, length(rayVec));
    float rayJitter = fract(sin(dot(pix, vec2(12.9898, 78.233))) * 43758.5453);
    float stepLen = maxRayDist / float(uMarchSteps);
    vec3 stepWorld = rayDir * stepLen;
    vec3 samplePos = uCameraPos + stepWorld * rayJitter;
    vec4 shadowClip = uShadowMatrix * vec4(samplePos, 1.0);
    vec4 shadowStep = uShadowMatrix * vec4(stepWorld, 0.0);
    for (int i = 0; i < 32; i++) {
      if (i >= uMarchSteps) break;
      if (samplePos.z > -2.98 && samplePos.z < 4.2 && samplePos.y > 0.0 && samplePos.y < 4.9 && samplePos.x > -3.8 && samplePos.x < 4.3) {
        vec3 lightToSample = samplePos - uLightPos;
        float distToLightSq = dot(lightToSample, lightToSample);
        vec3 L = lightToSample * inversesqrt(max(0.001, distToLightSq));
        float cosAngle = dot(L, uLightDir);
        float tWin = (-3.0 - uLightPos.z) / (samplePos.z - uLightPos.z + 0.00001);
        vec2 winHit = uLightPos.xy + (samplePos.xy - uLightPos.xy) * tWin;
        bool throughWindow = winHit.x > -0.58 && winHit.x < 2.18 && winHit.y > 1.55 && winHit.y < 3.75;
        float winEdge = smoothstep(-0.58, -0.38, winHit.x) * smoothstep(2.18, 1.98, winHit.x)
                      * smoothstep(1.55, 1.7, winHit.y) * smoothstep(3.75, 3.0, winHit.y);
        if (cosAngle > uLightCosAngle && throughWindow) {
          vec3 shadowNDC = shadowClip.xyz / max(0.0001, shadowClip.w);
          vec2 shadowUV = shadowNDC.xy * 0.5 + 0.5;
          float currentDepth = shadowNDC.z * 0.5 + 0.5;
          if (shadowUV.x > 0.002 && shadowUV.x < 0.998 && shadowUV.y > 0.002 && shadowUV.y < 0.998 && currentDepth < 1.0) {
            float closestDepth = texture(tShadowMap, shadowUV).r;
            if (currentDepth - 0.0022 < closestDepth) {
              float coneAtten = smoothstep(uLightCosAngle, uLightPenumbraCos, cosAngle);
              float mu = max(0.0, -dot(rayDir, L));
              float mu2 = mu * mu;
              float phase = 0.22 + 0.32 * mu2 + 0.36 * mu2 * mu2;
              float distAtten = 1.0 / (1.0 + 0.065 * distToLightSq);
              volumetricLight += uLightColor * (coneAtten * winEdge * distAtten * phase * stepLen * 0.62);
            }
          }
        }
      }
      samplePos += stepWorld;
      shadowClip += shadowStep;
    }
  }

  if (uFanLightOn > 0.5) {
    vec3 rayVecFan = surfacePos - uCameraPos;
    float rayLenFan = length(rayVecFan);
    vec3 rayDirFan = rayVecFan / max(0.0001, rayLenFan);
    float maxDistFan = min(rayLenFan, 12.0);
    float tClosest = clamp(dot(uFanLightPos - uCameraPos, rayDirFan), 0.0, maxDistFan);
    vec3 closestPt = uCameraPos + rayDirFan * tClosest;
    vec3 diffGlow = closestPt - uFanLightPos;
    float d2 = dot(diffGlow, diffGlow);
    float belowHub = smoothstep(0.10, -0.08, diffGlow.y);
    float bulbHaze = (0.014 / (0.045 + d2 * 2.2)) * belowHub;
    volumetricLight += vec3(1.0, 0.78, 0.46) * bulbHaze;
    if (rawDepth < 0.995 && surfacePos.z > -3.05 && surfacePos.y < 4.95) {
      vec3 lightToSurf = surfacePos - uFanLightPos;
      float surfDist = length(lightToSurf);
      vec3 Lfan = lightToSurf / max(0.0001, surfDist);
      float downCone = smoothstep(-0.15, 0.55, -Lfan.y);
      float roomAtten = (1.0 / (1.0 + 0.075 * surfDist * surfDist)) * downCone;
      float fillLum = clamp(dot(baseCol.rgb, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
      baseCol.rgb += (baseCol.rgb * 0.9 + vec3(0.07, 0.05, 0.028)) * vec3(1.0, 0.82, 0.54) * roomAtten * 0.85 * (1.0 - 0.75 * fillLum);
    }
  }

  vec3 combinedCol = baseCol.rgb + volumetricLight * uVolumetricStrength;
  combinedCol = clamp(combinedCol / (1.0 + combinedCol * 0.22), 0.0, 1.0);
  float gradeLum = dot(combinedCol, vec3(0.299, 0.587, 0.114));
  combinedCol = mix(vec3(gradeLum), combinedCol, uGradeSat);
  combinedCol = (combinedCol - 0.5) * uGradeContrast + 0.5;
  combinedCol *= uGradeTint;
  vec2 vigUv = vUv - 0.5;
  combinedCol *= 1.0 - uVignette * dot(vigUv, vigUv) * 2.2;
  combinedCol = clamp(combinedCol, 0.0, 1.0);
  float dither = bayer4x4(pix) * uDitherStrength;
  vec3 quantized = floor((combinedCol + dither) * 31.0 + 0.5) / 31.0;
  vec3 finalCol = mix(combinedCol, quantized, uDitherStrength);
  fragColor = vec4(finalCol, 1.0);
}
"""
}
