# Lotline: a cinematic thread

Research and implementation: 12 October 2026. This is a selection of strong references, not an objective ranking of the most interactive websites. The references informed the motion and narrative; Lotline's characters, vector artwork, copy, layouts and motion sequences are original.

## References and what they contributed

| Reference | Observed / documented direction | Lotline adaptation |
|---|---|---|
| [Lusion](https://lusion.co/) and [Synthetic Human](https://lusion.co/projects/synthetic_human/) | The studio describes distinctive brand worlds combining visual storytelling, design, motion and development. Its launch case connects visual craft to a product identity. | One recognizable paper character and a spatial hero, with a continuous thread as the brand's organizing idea. |
| [Lando Norris — OFF+BRAND case study](https://www.itsoffbrand.com/our-work/lando-norris) / [live site](https://landonorris.com/) | The creators explicitly describe speed-inspired animation, sharp transitions, responsive interactions and cinematic scrolling, supported by bold typography and 3D/WebGL. | Route changes carry the same graphic thread; huge editorial typography contrasts with precise small labels. The movement comes from Lotline's own identity. |
| [Siena Film Foundation](https://www.siena.film/) | The film catalogue includes an immersive exploration flow, drag navigation and direct film navigation. | Three named acts, a recurring cast, an opening sequence, and direct chapter controls alongside ordinary native scrolling. |
| [Starfish & Coffee — Marco Mori](https://www.marcomori.net/portfolio/starfish-coffee-brand-animation) | The artist documents a character-driven opening and related animated brand portraits. | LO reappears in the hero, cast and access screen, where its expression responds to password-field focus without reading the password. |
| [Tenkai Knights opening — Michael Cawood](https://michaelcawood.com/portfolio/tenkai-knights-website-intro/) | This historical 2013 example documents a character-led website cinematic, timed transparent effects and animation loops. | A short, composed sequence with character entrance, typography, thread drawing and curtain exit; it can be skipped or replayed. |

Further reference portals reviewed: [Active Theory](https://activetheory.net/), [Resn](https://resn.co.nz/), and [Awwwards storytelling collection](https://www.awwwards.com/websites/storytelling/). No ranking, external performance measurement or award claim is made for Lotline.

## The original cast

- **LO / Origin:** a folded carton with a lime paper strip, a readable face, lot mark and barcode motif. It represents the beginning of a lot's recorded story.
- **RO / Witness:** the teal variation represents the independent receiver's account.
- **CO / Sentinel:** the warm signal variation represents containment and unresolved responsibility.

Characters are illustrative metaphors. They do not assert authenticity or replace human operators. The public coffee case and hero scene are explicitly synthetic; the authenticated workbench remains backed by its real API.

## Implemented interactions

- Automatic opening on the first public visit in a browser session, approximately **2.85 seconds**, with immediate Skip/Escape and replay. Returning visitors enter directly. There is no artificial loading percentage, sound autoplay or requirement to wait before using the page.
- Layered SVG/CSS scene with pointer parallax, original character idle/wave/blink, three camera chapters and an orbit control. The scene pauses when offscreen, the page is hidden, or motion is reduced.
- GSAP thread drawing, native-scroll section arrivals and a scroll-linked background treatment. Motion assets load separately. Source: [GSAP ScrollTrigger documentation](https://gsap.com/docs/v3/Plugins/ScrollTrigger/).
- Independent synthetic root selection, hold activation and reset. Current received/unresolved/unaffected amounts remain explicit and are not conflated with historical parent quantities.
- Route transitions use the native Web Animations API: public surface arrival **380 ms** / thread wipe **560 ms**; daily workbench arrival **210 ms** / wipe **320 ms**. Query-only lot/edge/history selection does not replay a full-page transition or reset the graph viewport.
- Native dialogs and inbox surfaces arrive in **220 ms**. A profile button opens the existing workspace/operator settings; command palette, evidence actions and keyboard selection remain available.
- Persistent manual reduced-motion setting plus the OS preference. Both retain all content and workflows. The setting is available on the public footer and in the workspace menu.
- Responsive composition at 360, 768, 1024 and 1440 px. The paper cast becomes vertical cards on phones; the coffee diagram stays readable without document overflow.

The implementation uses original SVG and CSS rather than external actor images, video payloads or a 3D engine. It preserves a complete static presentation. No new runtime package or external media dependency was added; the existing GSAP dependency supplies the optional public motion.

## Verification

The additional `frontend/tests/cinema.spec.ts` exercises first-visit/skip/replay semantics, both scene sizes, chapter switching, orbit, synthetic state/reset, persisted motion preferences, offscreen pause, password-field focus, real navigation transitions, stable query-only selection and command palette access. The existing real coffee workflow, responsive/axe, stale-preview, onboarding and offline suites remain part of the final browser run. See [verification.md](verification.md) for executed results and the measured sample.

Screenshots: [opening](screenshots/opening-1440.png), [desktop story](screenshots/story-1440.png), [phone story](screenshots/story-360.png), [phone scene](screenshots/cinema-scene-360.png).
