import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';

export default defineConfig([
    tseslint.configs.recommendedTypeChecked,
    {
        rules: {
            "jsdoc/require-jsdoc": "off",
            "jsdoc/require-description": "off",
            "jsdoc/require-param-description": "off",
            "jsdoc/require-property-description": "off",
            "jsdoc/require-returns": "off",
            "jsdoc/require-returns-description": "off",
            eqeqeq: ["error", "smart"],
            camelcase: ["error", { properties: "never" }],
            "new-cap": "off",
            "no-console": "off",
            "no-use-before-define": ["error", { functions: false }],
            "no-undefined": "off"
        },
        languageOptions: {
            parserOptions: {
                projectService: true,
            },
        },
    }
]);
