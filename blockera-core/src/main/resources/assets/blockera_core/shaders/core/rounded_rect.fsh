#version 150

uniform vec2 Size;
uniform float Radius;
uniform float BorderWidth;
uniform float Softness;
uniform vec4 FillColor;
uniform vec4 BorderColor;

in vec2 texCoord;
out vec4 fragColor;

float roundedBox(vec2 point, vec2 halfSize, float radius) {
    vec2 distanceToEdge = abs(point) - max(halfSize - vec2(radius), vec2(0.0));
    return length(max(distanceToEdge, vec2(0.0))) + min(max(distanceToEdge.x, distanceToEdge.y), 0.0) - radius;
}

void main() {
    vec2 halfSize = Size * 0.5;
    vec2 point = (texCoord - vec2(0.5)) * Size;
    float outerDistance = roundedBox(point, halfSize, Radius);
    float outerAlpha = 1.0 - smoothstep(-Softness, Softness, outerDistance);
    float innerAlpha = 1.0;
    if (BorderWidth > 0.001) {
        vec2 innerHalf = max(halfSize - vec2(BorderWidth), vec2(0.0));
        float innerRadius = max(Radius - BorderWidth, 0.0);
        float innerDistance = roundedBox(point, innerHalf, innerRadius);
        innerAlpha = 1.0 - smoothstep(-Softness, Softness, innerDistance);
    }
    vec4 color = mix(BorderColor, FillColor, innerAlpha);
    fragColor = vec4(color.rgb, color.a * outerAlpha);
}
