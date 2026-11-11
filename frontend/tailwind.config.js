/** Colors resolve from CSS variables so the Super Admin's theme settings apply live. */
const c = (name) => `rgb(var(--c-${name}) / <alpha-value>)`
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: c('primary'), ink: c('secondary'), canvas: c('background'),
        surface: c('surface'), accent: c('accent'), body: c('text'),
      },
      fontFamily: {
        heading: ['var(--font-heading)', 'system-ui', 'sans-serif'],
        sans: ['var(--font-body)', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
