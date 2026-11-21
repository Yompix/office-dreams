shader_type canvas_item;
// 2.0.2: монохром бренда Yompix для того, что рисует цвет само (костёр, фонари, аватары с пламенем):
// яркость сохраняется, цвет уходит в серый, чуть контраста.
uniform float contrast = 1.12;
void fragment() {
	vec4 c = COLOR;
	float l = dot(c.rgb, vec3(0.299, 0.587, 0.114));
	l = clamp((l - 0.5) * contrast + 0.5, 0.0, 1.0);
	COLOR = vec4(vec3(l), c.a);
}
