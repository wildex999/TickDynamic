package patcher;

public class ValueType {
	public enum Type {
		TBoolean,
		TInteger,
		TFloat,
		TDouble,
		TLong,
		TShort,
		TByte,
		TChar,
		TString,
		TType
	};
	public Type type;
	public Object value;
}
