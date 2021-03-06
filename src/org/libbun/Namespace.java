package org.libbun;

public class Namespace extends SymbolTable {
	public UniMap<PegParser> parserMap;
	public UniArray<String> exportSymbolList;
	public BunDriver  driver;

	public Namespace(PegParser parser, BunDriver driver) {
		super(null);
		this.root = this;
		this.parserMap = new UniMap<PegParser>();
		this.parserMap.put("main", parser);
		this.driver = driver;
	}
	
	public final String toString() {
		return "root";
	}


	public void importFrom(Namespace ns) {
		for(int i = 0; i < ns.exportSymbolList.size(); i++) {
			String symbol = ns.exportSymbolList.ArrayValues[i];
			this.setSymbol(symbol, ns.getSymbol(symbol));
		}
	}

	public ParserContext newParserContext(String lang, PegSource source) {
		if(lang == null) {
			lang = this.guessLang(source.fileName, "bun");
		}
		PegParser parser = this.getParser(lang);
		return parser.newContext(source);
	}

	private String guessLang(String fileName, String defaultValue) {
		if(fileName != null) {
			int loc = fileName.lastIndexOf(".");
			if(loc > 0) {
				return fileName.substring(loc+1);
			}
		}
		return defaultValue;
	}

	public final PegParser getParser(String lang) {
		PegParser p = this.parserMap.get(lang);
		if(p == null) {
			p = new PegParser();
			p.loadPegFile("lib/peg/" + lang + ".peg");
			this.parserMap.put(lang, p);
		}
		return p;
	}



}
