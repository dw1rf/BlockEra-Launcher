import cssText from '@modrinth/assets/styles/ace.css?raw'
import ace from 'ace-builds'

type ThemeExports = { isDark: boolean; cssClass: string; cssText: string }
type DomModule = {
	importCssString: (text: string, className: string, useStrictCSP: boolean) => void
}
const aceModules = ace as typeof ace & {
	define: (
		name: string,
		dependencies: string[],
		factory: (
			require: (name: 'ace/lib/dom') => DomModule,
			exports: ThemeExports,
			module: unknown,
		) => void,
	) => void
}

aceModules.define(
	'ace/theme/modrinth',
	['require', 'exports', 'module', 'ace/lib/dom'],
	function (require, exports, _module) {
		exports.isDark = false
		exports.cssClass = 'ace-modrinth'
		exports.cssText = cssText

		const dom = require('ace/lib/dom')
		dom.importCssString(exports.cssText, exports.cssClass, false)
	},
)
