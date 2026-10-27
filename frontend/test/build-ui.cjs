require('esbuild').buildSync({entryPoints:['test/ui-harness.tsx'],outfile:'.test-build/ui.mjs',bundle:true,platform:'node',format:'esm',packages:'external',jsx:'automatic'});
