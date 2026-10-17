# H2 interface decisions

The reference pass was deliberately short:

- Linear's current product and published UI redesign discussion: https://linear.app/now/how-we-redesigned-the-linear-ui
- Raycast's current desktop product: https://www.raycast.com/

Applied principles: consistent alignment across the sidebar/header/content, distinct surface levels, typography with a clear hierarchy, keyboard-accessible controls and status that describes real work. These are design inferences, not claims that KapKan copies either product.

The UI/UX skill's broad gaming preset suggested neon/3D styling that conflicted with the brief. A narrower minimal desktop search was used instead. KapKan's supplied artwork, logo and crimson remain the visual identity. All optional motion is disabled, covering reduced-motion preferences without an animated background.

Base surface: #0F1114. Raised panel: #17191D. Controls: #212429. Primary action: #C9263F. Text: #F2F3F5. Secondary text: #AAB1BC. No third-party avatar fetches or online-status dots.

The supplied logo is an opaque RGB white-on-black image. No transparent original exists in H1. The badge texture is derived deterministically: original luminance becomes alpha, all RGB channels become white, and the image is tightly framed without tracing or redrawing. The unmodified official original is included for provenance. This retains the sampled silhouette and proportions, but cannot preserve a pre-existing transparent alpha channel that was not supplied.

## H3 refinement of the approved composition

The owner's H2 screenshot and approval define the visual baseline. H3 preserves the artwork, hero composition, brand placement and functional structure. The profile becomes an intrinsically sized name/type control with an account menu. Inactive navigation loses its heavy rectangles; only the current page carries crimson. Launch and form controls gain consistent small corner radii and fine surface edges. Keyboard focus remains explicit and appears when navigating with keys. The controls do not animate or add background work. No new reference collection or unrelated visual direction was introduced.

## H4 focused account-menu refinement

The owner's cropped H3 popup is the reference for this pass. The name/type rectangle becomes a single-line name and chevron with a 44-pixel hit area. The expanded popup uses a 12-pixel corner radius, an 8-pixel shadow margin, a fine light edge and restrained charcoal depth. Playing as establishes the current-account hierarchy; alternative profiles use compact rows and short Guest/Microsoft labels. Manage accounts has its own separated footer. There are no avatars or new visual assets. Pointer-open menus have no artificial selected-row fill, while arrow-key navigation retains a focus outline. The rest of H3 remains unchanged.

## H5 Mac title-bar color only

The supplied Mac screenshots show white system title bars on the main frame and launch-log dialog. H5 requests NSAppearanceNameDarkAqua before Java initializes its native UI. Existing native controls, titles, window behavior and all H4 content styling are retained. There is no full-window-content conversion or custom window chrome. The exact Mac result must be checked on the user's device; Linux rendering is not a substitute.


## Internal build 6

Preserve approved Play/profile composition and assets. Inner tabs now use shared dark surfaces, clearer hierarchy and control grouping. Mod search uses actual filenames; settings hide optional service details and autosave badge switches. No animation or image assets added.
