package com.ubertob.fotf.exercises.chapter4

// 함수 타입에 대한 타입 별정 정의
typealias FUN<A, B> = (A) -> B

// The infix function
infix fun <A : Any, B : Any, C : Any> FUN<A, B?>.andUnlessNull(other: FUN<B, C?>): FUN<A, C?> =
    { a: A -> this(a)?.let(other) }
