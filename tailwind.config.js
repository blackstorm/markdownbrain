/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./server/**/*.{html,js,go}", "./cli/**/*.{html,js,go}"],
  theme: {
    extend: {},
  },
  plugins: [require("@tailwindcss/typography")],
}

