/*
 * Copyright (c) 2011 Lingway
 */

package groovyx.ast.bytecode

import groovyjarjarasm.asm.Label
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.classgen.BytecodeInstruction
import org.codehaus.groovy.classgen.BytecodeSequence
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class BytecodeASTTransformation implements ASTTransformation, Opcodes {
	void visit(ASTNode[] nodes, SourceUnit source) {
		def meth = nodes[1]
		def instructions = meth.code.statements
		meth.code = new BytecodeSequence(new BytecodeInstruction() {
			@Override
			void visit(MethodVisitor mv) {
				def labels = [:]
				// perform first visit to collect labels
				instructions.each { ExpressionStatement stmt ->
					def expression = stmt.expression
					if (expression instanceof VariableExpression) {
						def text = expression.text
						if (text ==~ /l[0-9]+/) {
							labels.put(text, new Label())
						}
					}
				}
				instructions.each { ExpressionStatement stmt ->
					def expression = stmt.expression
					if (expression instanceof VariableExpression) {
						def text = expression.text
						if (text ==~ /l[0-9]+/) {
							mv.visitLabel(labels[text])
						} else if (text =~ /[aild]const|[aild]sub|[aild]add|[aild]return/) {
							mv.visitInsn(Opcodes."${text.toUpperCase()}")
						} else {
							throw new IllegalArgumentException("Bytecode operation unsupported : "+text);
						}
					} else if (expression instanceof MethodCallExpression) {
						if (expression.objectExpression instanceof VariableExpression && expression.arguments instanceof ArgumentListExpression) {
							if (expression.objectExpression.text=="this") {
								def opcode = expression.methodAsString.toUpperCase()
								ArgumentListExpression args = expression.arguments
								switch (opcode) {
									case '_GOTO':
										mv.visitJumpInsn(GOTO, labels[args.expressions[0].text])
										break;
									case 'IF_ICMPGE':
									case 'IF_ICMPLE':
									case 'IF_ICMPNE':
									case 'IF_ICMPLT':
									case 'IF_ICMPGT':
										mv.visitJumpInsn(Opcodes."${opcode}", labels[args.expressions[0].text])
										break;
									case 'ALOAD':
									case 'ILOAD':
                                    case 'ISTORE':
                                    case 'ASTORE':
										mv.visitVarInsn(Opcodes."${opcode}", args.expressions[0].text as int)
										break;
                                    case 'IINC':
                                        mv.visitIincInsn(args.expressions[0].text as int, args.expressions[1].text as int)
                                        break;
									case 'INVOKEVIRTUAL':
                                    case 'INVOKESTATIC':
                                    case 'INVOKESPECIAL':
										def classExpr = args.expressions[0].text
                                        def clazz,call
                                        if (classExpr[0]=='.') {
                                            clazz = meth.declaringClass.name
                                            call = classExpr[1..<classExpr.length()]
                                        } else {
                                            def index = classExpr.lastIndexOf('.')
                                            clazz = classExpr.substring(0, index).replaceAll(/\./,'/')
                                            call = classExpr.substring(index+1)
                                        }

										def signature = args.expressions[1].text
										mv.visitMethodInsn(Opcodes."${opcode}", clazz, call, signature)
										break;
									case 'FRAME':
										// frames only supported in JDK 1.6+
									default:
										throw new IllegalArgumentException("Bytecode operation unsupported : "+expression);
								}
							} else {
								throw new IllegalArgumentException("Bytecode operation unsupported : "+expression);
							}
						} else {
							throw new IllegalArgumentException("Bytecode operation unsupported : "+expression);
						}
					} else {
						throw new IllegalArgumentException("Bytecode operation unsupported : "+expression);
					}
				}
			}
		})
	}

}
