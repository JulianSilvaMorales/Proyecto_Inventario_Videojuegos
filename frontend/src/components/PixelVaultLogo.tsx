import { useId } from 'react'

/**
 * Logo de PixelVault: la puerta de una bóveda en pixel art (16 × 16).
 * `#` = marco, bisagras y dial (degradado violeta); `o` = manija (cian).
 * public/favicon.svg usa la misma cuadrícula.
 */
const PIXELS = [
  '................',
  '.##############.',
  '.#............#.',
  '##....####....#.',
  '##...#....#...#.',
  '.#..#..oo..#..#.',
  '.#.#...oo...#.#.',
  '.#.#.oooooo.#.#.',
  '.#.#.oooooo.#.#.',
  '.#.#...oo...#.#.',
  '.#..#..oo..#..#.',
  '##...#....#...#.',
  '##....####....#.',
  '.#............#.',
  '.##############.',
  '..##........##..',
]

function pixels(symbol: string) {
  return PIXELS.flatMap((row, y) =>
    [...row].flatMap((cell, x) => (cell === symbol ? [{ x, y }] : [])),
  )
}

const FRAME = pixels('#')
const HANDLE = pixels('o')

interface PixelVaultLogoProps {
  size?: number
  title?: string
}

export function PixelVaultLogo({ size = 40, title }: PixelVaultLogoProps) {
  const gradient = `pixelvault-${useId().replace(/:/g, '')}`
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      shapeRendering="crispEdges"
      role={title ? 'img' : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
    >
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#C084FC" />
          <stop offset="1" stopColor="#7C3AED" />
        </linearGradient>
      </defs>
      {FRAME.map(({ x, y }) => (
        <rect key={`f${x}-${y}`} x={x} y={y} width="1" height="1" fill={`url(#${gradient})`} />
      ))}
      {HANDLE.map(({ x, y }) => (
        <rect key={`h${x}-${y}`} x={x} y={y} width="1" height="1" fill="#22D3EE" />
      ))}
    </svg>
  )
}
