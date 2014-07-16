package org.libbun.drv;

import org.libbun.BunType;
import org.libbun.Functor;
import org.libbun.Main;
import org.libbun.Namespace;
import org.libbun.UMap;
import org.libbun.peg4d.PegObject;

public class JsDriver extends SourceDriver {

    public JsDriver() {
	
    }

	@Override
	public String getDesc() {
	    return "Javascript source generator by Masaki Ishii (YNU)";
	}

	@Override
	public void initTable(Namespace gamma) {
	    gamma.loadBunModel("lib/driver/js/common.bun", this);
	}

	@Override
	public void pushType(BunType type) {
	}

	@Override
	public void pushApplyNode(String name, PegObject args) {
	}
	public void generateMain() {
		
	}
}
