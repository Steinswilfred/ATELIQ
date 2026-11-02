/** Colors resolve to CSS variables so the Super Admin's theme settings can restyle the site at runtime. */
const v = (name) => `rgb(var(--${name}) / <alpha-value>)`;

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: v('primary'),
        'primary-soft': v('primary-soft'),
        ink: v('ink'),
        canvas: v('background'),
        surface: v('surface'),
        body: v('text'),
      },
      fontFamily: {
        display: ['var(--font-display)', 'Georgia', 'serif'],
        sans: ['var(--font-body)', 'system-ui', 'sans-serif'],
      },
      borderRadius: { card: '14px' },
    },
  },
  plugins: [],
};
