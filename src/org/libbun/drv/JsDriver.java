package org.libbun.drv;

import org.libbun.BunDriver;
import org.libbun.BunType;
import org.libbun.DriverCommand;
import org.libbun.Functor;
import org.libbun.Main;
import org.libbun.Namespace;
import org.libbun.UMap;
import org.libbun.drv.JvmDriver.PushAsLong;
import org.libbun.peg4d.PegObject;

public class JsDriver extends SourceDriver {

	public JsDriver() {
		this.addCommand("NewArray", new NewArray());
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
    protected class NewArray extends DriverCommand {
		@Override
		public void invoke(BunDriver driver, PegObject node, String[] param) {
			StringBuffer code = new StringBuffer();
			int size = node.size();
			code.append("new Array(");
			for(int i = 0; i < size; i++) {
				code.append(node.AST[i].getText());
				if(i != size - 1) {
					code.append(", ");
				}
			}
			code.append(")");
			driver.pushCode(code.toString());
		}
	}
}
