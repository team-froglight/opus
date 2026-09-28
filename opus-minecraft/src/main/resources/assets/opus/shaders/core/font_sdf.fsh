#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float distance = texture(Sampler0, texCoord).a - (128.0 / 255.0);
    float pixelWidth = max(fwidth(distance), 1.0 / 255.0);
    float coverage = smoothstep(-0.5 * pixelWidth, 0.5 * pixelWidth, distance);
    // Keep the low-alpha edge samples: vanilla's textured shader discards them.
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= coverage;
}
