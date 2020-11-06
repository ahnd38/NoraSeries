package org.jp.illg.util.object;

public interface FunctionTriple<T1, T2, T3, R> {

	R apply(T1 t1, T2 t2, T3 t3);
}
