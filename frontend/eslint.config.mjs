import js from '@eslint/js';
import ts from 'typescript-eslint';
import globals from 'globals';

export default ts.config(
  {ignores:['dist/**','.test-build/**','node_modules/**']},
  js.configs.recommended,
  ...ts.configs.recommended,
  {files:['src/**/*.{ts,tsx}'],languageOptions:{globals:globals.browser},rules:{'no-empty':['error',{allowEmptyCatch:true}],'@typescript-eslint/no-unused-vars':['error',{argsIgnorePattern:'^_',caughtErrorsIgnorePattern:'^_'}]}}
);
