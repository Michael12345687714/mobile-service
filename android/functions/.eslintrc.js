module.exports = {
  env: {
    es6: true,
    node: true,
  },
  parserOptions: {
    "ecmaVersion": 2018,
  },
  extends: [
    "eslint:recommended",
  ],
  rules: {
    "no-restricted-globals": ["error", "name", "length"],
    "prefer-arrow-callback": "error",
    "quotes": "off",           // Deshabilitar regla de comillas
    "indent": "off",           // Deshabilitar regla de indentación
    "max-len": "off",          // Deshabilitar límite de línea
    "object-curly-spacing": "off", // Deshabilitar espacios en objetos
    "comma-dangle": "off",     // Deshabilitar comas finales
  },
  overrides: [
    {
      files: ["**/*.spec.*"],
      env: {
        mocha: true,
      },
      rules: {},
    },
  ],
  globals: {},
};