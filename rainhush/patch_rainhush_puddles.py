#!/usr/bin/env python3
"""Rainhush 0.5.9: two translucent, gradually forming puddles at the taskbar edge.
Apply after rainhush/patch_rainhush.py, which follows snowhush-toggle/patch_snowhush.py.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1]) if len(sys.argv)>1 else Path("snowhush")
def patch(name,changes):
    f=root/name
    s=f.read_text(encoding="utf-8")
    for old,new in changes:
        count=s.count(old)
        if count!=1: raise RuntimeError(f"{name}: expected one anchor but found {count}: {old[:80]!r}")
        s=s.replace(old,new,1)
    f.write_text(s,encoding="utf-8")
    print("Patched",name)

patch("windows/Cargo.toml",[('version = "0.5.8"','version = "0.5.9"')])
patch("windows/src/simulation.rs",[
('    target_flakes: usize,\n    strength_target: f32,',
 '''    target_flakes: usize,
    /// Virtual seconds of rain; drying runs in reverse after the rain stops.
    puddle_rain_time: f32,
    strength_target: f32,'''),
('            target_flakes: 0,\n            strength_target: 1.0,',
 '''            target_flakes: 0,
            puddle_rain_time: 0.0,
            strength_target: 1.0,'''),
('    pub fn sprite_count(&self) -> usize {\n        self.air.flakes.len() + self.air.grains.len() + self.air.motes.len()\n    }',
 '''    pub fn sprite_count(&self) -> usize {
        self.air.flakes.len() + self.air.grains.len() + self.air.motes.len() + self.puddle_count()
    }

    fn puddle_amount(&self, start: f32) -> f32 {
        let x = ((self.puddle_rain_time - start) / 38.0).clamp(0.0, 1.0);
        x * x * (3.0 - 2.0 * x)
    }

    fn puddle_count(&self) -> usize {
        [30.0, 82.0].into_iter().filter(|&start| self.puddle_amount(start) > 0.001).count()
    }'''),
('        self.air.clock += dt as f64;\n        let was_moving = self.sprite_count() > 0;',
 '''        self.air.clock += dt as f64;
        let was_moving = self.sprite_count() > 0;
        let rain_speed = match self.snowfall {
            Snowfall::Off => -4.0,
            Snowfall::Light => 0.60,
            Snowfall::Steady => 1.0,
            Snowfall::Heavy => 1.65,
            Snowfall::Variable => self.air.strength.clamp(0.4, 1.8),
        };
        self.puddle_rain_time = (self.puddle_rain_time + dt * rain_speed).clamp(0.0, 145.0);'''),
('        let mut count = 0;\n        for (slot, sprite) in buffer.iter_mut().zip(flakes.chain(grains).chain(motes)) {',
 '''        // The normal overlay ends at the top of the taskbar: no clicks are blocked.
        let puddles = [(0.16, 30.0, 0.0), (0.81, 82.0, 1.0)].into_iter().filter_map(|(fraction, start, phase)| {
            let amount = self.puddle_amount(start);
            (amount > 0.001).then(|| Sprite {
                position: V2::new(self.width * fraction, 2.5),
                velocity: V2::new(32.0 + 74.0 * amount, 1.8 + 3.1 * amount),
                size: 1.0,
                alpha: amount * (0.22 + 0.055 * phase),
                tint: [0.40, 0.70, 0.95],
                tint_amount: 1.0,
                lump: -1.0,
                ..Sprite::default()
            })
        });
        let mut count = 0;
        for (slot, sprite) in buffer.iter_mut().zip(flakes.chain(grains).chain(motes).chain(puddles)) {''')
])
patch("windows/src/shaders.hlsl",[
('SpriteOut spriteVertex(SpriteIn s) {\n    float pixels = s.size * scale;',
 '''SpriteOut spriteVertex(SpriteIn s) {
    if (s.lump < 0.0) {
        float width = max(s.velocity.x * scale, 1.0);
        float height = max(s.velocity.y * scale, 1.0);
        float2 corner = float2(float(s.vid & 1), float(s.vid >> 1));
        SpriteOut puddle;
        float2 center = s.position / viewSize * 2.0 - 1.0;
        puddle.position = float4(center + (corner - 0.5) * width * 2.0 / (viewSize * scale), 0.0, 1.0);
        puddle.coord = float2(corner.x, 1.0 - corner.y);
        puddle.streak = float2(1.0, height / width);
        puddle.radius = 0.5;
        puddle.alpha = saturate(s.alpha);
        puddle.soft = 1.0;
        puddle.tint = float4(s.tintColor, s.tintAmount);
        puddle.lump = -1.0;
        puddle.spin = 0.0;
        return puddle;
    }
    float pixels = s.size * scale;'''),
('float4 spriteFragment(SpriteOut i) : SV_Target {\n    // Quad coordinates run down the screen; velocities run up.',
 '''float4 spriteFragment(SpriteOut i) : SV_Target {
    if (i.lump < 0.0) {
        float2 p = float2(i.coord.x - 0.5, 0.5 - i.coord.y);
        float2 axes = max(i.streak * 0.5, float2(0.001, 0.001));
        float2 q = p / axes;
        float r = length(q);
        float softEdge = 1.0 - smoothstep(0.78, 1.0, r);
        if (softEdge < 0.002) discard;
        float edge = smoothstep(0.72, 0.98, r);
        float shimmer = 0.5 + 0.5 * sin(time * 0.75 + q.x * 9.0);
        float gleam = pow(saturate(1.0 - abs(q.y - 0.29) * 3.2), 8.0) * (0.12 + shimmer * 0.1);
        float3 water = lerp(float3(0.22, 0.39, 0.58), float3(0.61, 0.81, 0.97), edge * 0.48 + gleam);
        water *= light.rgb;
        float a = i.alpha * softEdge * (0.83 + 0.17 * edge);
        return float4(water * a, a);
    }
    // Quad coordinates run down the screen; velocities run up.''')
])
print("Rainhush PiiWii 0.5.9 puddle patch complete")
