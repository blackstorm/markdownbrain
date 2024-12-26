/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./templates/**/*.{html,js}", "./www/static/app.js"],
  theme: {
    extend: {},
  },
  plugins: [require("@tailwindcss/typography")],
}

