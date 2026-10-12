import { useId } from 'react';

/** Original vector cast. The character is a brand metaphor, never an inventory assertion. */
export default function LotCharacter({
  variant = 'origin',
  expression = 'awake',
  className = '',
}: {
  variant?: 'origin' | 'witness' | 'sentinel';
  expression?: 'awake' | 'secret';
  className?: string;
}) {
  const id = useId().replaceAll(':', '');
  const accent = variant === 'sentinel' ? '#ed9470' : variant === 'witness' ? '#77d7bf' : '#dbf598';
  return (
    <svg
      className={`lot-character character-${variant} expression-${expression} ${className}`}
      viewBox="0 0 360 420"
      fill="none"
      aria-hidden="true"
    >
      <defs>
        <linearGradient
          id={`${id}-front`}
          x1="103"
          y1="160"
          x2="290"
          y2="330"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#fffbea" />
          <stop offset="1" stopColor="#dedbc5" />
        </linearGradient>
        <linearGradient
          id={`${id}-top`}
          x1="117"
          y1="80"
          x2="277"
          y2="175"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#f8f6e7" />
          <stop offset="1" stopColor="#c8ceb6" />
        </linearGradient>
        <linearGradient
          id={`${id}-side`}
          x1="283"
          y1="132"
          x2="320"
          y2="328"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#a6b699" />
          <stop offset="1" stopColor="#627b61" />
        </linearGradient>
      </defs>
      <ellipse
        className="character-shadow"
        cx="191"
        cy="376"
        rx="105"
        ry="18"
        fill="#071c16"
        opacity=".2"
      />
      <g className="character-leg left-leg">
        <path d="M139 321L132 369" stroke="#254f40" strokeWidth="13" strokeLinecap="round" />
        <path d="M132 370L105 375" stroke="#183b30" strokeWidth="19" strokeLinecap="round" />
      </g>
      <g className="character-leg right-leg">
        <path d="M234 331L244 372" stroke="#254f40" strokeWidth="13" strokeLinecap="round" />
        <path d="M244 373L271 377" stroke="#183b30" strokeWidth="19" strokeLinecap="round" />
      </g>
      <g className="character-body">
        <path
          className="character-arm left-arm"
          d="M104 224C65 231 85 284 49 274"
          stroke="#d7ddbf"
          strokeWidth="12"
          strokeLinecap="round"
        />
        <path
          className="character-arm right-arm"
          d="M298 209C329 194 320 157 336 148"
          stroke="#d7ddbf"
          strokeWidth="12"
          strokeLinecap="round"
        />
        <path d="M105 135L155 91L307 123L258 174Z" fill={`url(#${id}-top)`} />
        <path d="M258 174L307 123V291L258 342Z" fill={`url(#${id}-side)`} />
        <path d="M105 135L258 174V342L105 303Z" fill={`url(#${id}-front)`} />
        <path d="M168 149L218 105L249 112L199 157Z" fill={accent} />
        <path d="M168 149L199 157V334L168 326Z" fill={accent} opacity=".8" />
        <path d="M110 140L253 177M263 179L300 140" stroke="#fffdf0" strokeWidth="2" opacity=".6" />
        <path d="M128 183L232 208V242L128 218Z" fill="#153c30" />
        {expression === 'secret' ? (
          <g stroke={accent} strokeWidth="4">
            <path d="M141 200L157 204M201 216L217 220" />
          </g>
        ) : (
          <g className="character-eyes" fill={accent}>
            <ellipse cx="150" cy="204" rx="6" ry="9" transform="rotate(-12 150 204)" />
            <ellipse cx="210" cy="218" rx="6" ry="9" transform="rotate(-12 210 218)" />
          </g>
        )}
        <path d="M166 237Q183 251 198 245" stroke="#536c54" strokeWidth="3" strokeLinecap="round" />
        <path d="M123 253L156 261V281L123 273Z" fill="#294b3c" />
        <text
          x="128"
          y="271"
          fill="#fff9e7"
          fontFamily="monospace"
          fontSize="12"
          transform="rotate(14 128 271)"
        >
          LO
        </text>
        <g stroke="#65755b" strokeWidth="2">
          <path d="M213 269V296M217 270V297M223 272V299M227 273V300M233 275V302M238 276V303" />
        </g>
        <circle cx="283" cy="248" r="8" fill={accent} />
        <path d="M177 119L179 60" stroke="#89a77f" strokeWidth="3" />
        <circle cx="179" cy="58" r="7" fill={accent} />
        {variant === 'witness' && <path d="M266 275L302 238L317 243L281 280Z" fill="#78d7bc" />}
        {variant === 'sentinel' && (
          <g>
            <path d="M174 63L190 30L204 68Z" fill="#ed9470" />
            <path d="M189 42V54M189 60V62" stroke="#173b30" strokeWidth="3" />
          </g>
        )}
      </g>
    </svg>
  );
}
