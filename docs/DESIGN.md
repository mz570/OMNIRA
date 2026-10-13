# UI design system

OMNIRA uses a mint-green brand palette, soft neutral surfaces, and rounded, raised components. The visual language is implemented in Compose and shared through `ui/theme`.

## Brand palette

| Token | Color | Usage |
| --- | --- | --- |
| `GreenPrimary` | `#3DD78D` | Primary actions, selected states, app bars |
| `GreenDark` | `#065F46` | Darker green contrast |
| `YellowSecondary` | `#FDC02E` | Secondary highlight |
| `NeumorphicBackground` | `#ECF0F3` | Main background/surface |
| `NeumorphicLightShadow` | `#FFFFFF` | Raised component highlight |
| `NeumorphicDarkShadow` | `#D1D9E6` | Raised component shadow |
| `GrayText` | `#757575` | Secondary copy |

Color values are defined in `app/src/main/java/com/omnira/ui/theme/Color.kt`.

## Typography

`Type.kt` defines the Material typography scale. Display and major heading styles use a bold serif face; titles, body copy, and labels use sans serif. Prefer Material typography tokens (`titleMedium`, `bodyLarge`, `labelSmall`, etc.) instead of introducing screen-specific sizes.

## Components and interaction

- Use Material 3 components for navigation bars, app bars, tabs, fields, dialogs, badges, and buttons.
- Use rounded corners; common surfaces use 8–28 dp corner radii depending on component size.
- Apply `Modifier.neumorphic` for raised surfaces where it improves hierarchy. Avoid layering it on every nested element.
- Use `GreenPrimary` for active/positive actions and reserve red for destructive actions.
- Pair icons with content descriptions and keep interactive targets clear.
- Use `AsyncImage` (Coil) for remote profile/class images and provide an initial-letter fallback.
- Preserve responsive behavior for portrait and landscape layouts.

## Theme entry point

Wrap app content in `OMNIRATheme` from `Theme.kt`. The theme exposes matching light/dark Material color schemes, with the app's brand green applied to system bars.

## Adding or changing UI

1. Reuse an existing screen/component pattern before creating another visual treatment.
2. Use theme tokens and Compose Material typography instead of hard-coded colors where possible.
3. Keep loading, empty, success, and failure states visible.
4. Add accessibility labels to icon-only controls and avoid conveying status with color alone.
5. Verify the result in both portrait and landscape, and in light/dark system settings.
