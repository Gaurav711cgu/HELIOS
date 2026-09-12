/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        neon: '#00FF66',
        graphite: '#333333'
      }
    },
  },
  plugins: [],
}
