#version 150

uniform vec2 ShapeSize;
// Clockwise from the top left; coordinates are in GUI units.
uniform vec4 CornerRadii;
uniform vec4 FillColor;
uniform vec4 EdgeColor;
uniform vec4 ColorModulator;
uniform float StrokeWidth;
uniform float ShadowBlur;
uniform vec4 Cutout;
uniform int HasCutout;

in vec2 localPosition;
out vec4 fragColor;

float roundedDistance(vec2 p, vec2 halfSize, vec4 radii) {
    float radius = p.x < 0.0
        ? (p.y < 0.0 ? radii.x : radii.w)
        : (p.y < 0.0 ? radii.y : radii.z);
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float cutoutDistance(vec2 p, float expansion) {
    vec2 center = (Cutout.xy + Cutout.zw) * 0.5;
    vec2 halfSize = (Cutout.zw - Cutout.xy) * 0.5 + expansion;
    vec2 q = abs(p - center) - halfSize;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0));
}

float coverage(float distance) {
    // Derivatives keep the transition one framebuffer pixel wide at any GUI scale.
    float pixelWidth = max(fwidth(distance), 0.0001);
    return 1.0 - smoothstep(-0.5 * pixelWidth, 0.5 * pixelWidth, distance);
}

void main() {
    vec2 halfSize = ShapeSize * 0.5;
    vec2 p = localPosition - halfSize;
    float outerDistance = roundedDistance(p, halfSize, CornerRadii);
    if (HasCutout != 0) outerDistance = max(outerDistance, -cutoutDistance(localPosition, 0.0));
    float outerCoverage = coverage(outerDistance);
    if (ShadowBlur > 0.0) {
        float shadowCoverage = 1.0 - smoothstep(-ShadowBlur, ShadowBlur, outerDistance);
        fragColor = vec4(FillColor.rgb, FillColor.a * shadowCoverage) * ColorModulator;
        return;
    }
    float innerCoverage = outerCoverage;
    if (StrokeWidth > 0.0) {
        vec2 innerHalfSize = halfSize - StrokeWidth;
        float innerDistance = roundedDistance(p, max(innerHalfSize, vec2(0.0)),
            max(CornerRadii - StrokeWidth, vec4(0.0)));
        if (HasCutout != 0) innerDistance = max(innerDistance, -cutoutDistance(localPosition, StrokeWidth));
        innerCoverage = min(outerCoverage, coverage(innerDistance));
        if (min(innerHalfSize.x, innerHalfSize.y) <= 0.0) innerCoverage = 0.0;
    }
    // Composite fill and stroke once, avoiding a doubled fringe on translucent edges.
    float fillAlpha = FillColor.a * innerCoverage;
    float edgeAlpha = EdgeColor.a * (outerCoverage - innerCoverage);
    float alpha = fillAlpha + edgeAlpha;
    vec3 rgb = (FillColor.rgb * fillAlpha + EdgeColor.rgb * edgeAlpha) / max(alpha, 0.00001);
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
