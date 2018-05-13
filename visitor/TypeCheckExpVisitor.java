package visitor;

import syntaxtree.*;

import java.util.ArrayList;

public class TypeCheckExpVisitor extends TypeDepthFirstVisitor {

  private Class currClass;
  private Method currMethod;
  private SymbolTable symbolTable;

  public TypeCheckExpVisitor(Class currClass, Method currMethod, SymbolTable symbolTable) {
    this.currClass = currClass;
    this.currMethod = currMethod;
    this.symbolTable = symbolTable;
  }

  // Exp e1,e2;
  public Type visit(And n) {
    if (!(n.e1.accept(this) instanceof BooleanType)) {
      System.err.printf("Left side of AND must be of type boolean (%s,%s:%s)%n", 0, 0, 0);
      return null;
    }

    if (!(n.e2.accept(this) instanceof BooleanType)) {
      System.err.printf("Right side of AND must be of type boolean (%s,%s:%s)%n", 0, 0, 0);
      return null;
    }

    return new BooleanType();
  }

  // Exp e1,e2;
  public Type visit(LessThan n) {
    if (n.e1.accept(this) instanceof IntegerType && n.e2.accept(this) instanceof IntegerType) {
      return new BooleanType();
    }

    return null;
  }

  // Exp e1,e2;
  public Type visit(Plus n) {
    if (n.e1.accept(this) instanceof IntegerType && n.e2.accept(this) instanceof IntegerType) {
      return new IntegerType();
    }

    return null;
  }

  // Exp e1,e2;
  public Type visit(Minus n) {
    if (n.e1.accept(this) instanceof IntegerType && n.e2.accept(this) instanceof IntegerType) {
      return new IntegerType();
    }

    return null;
  }

  // Exp e1,e2;
  public Type visit(Times n) {
    if (n.e1.accept(this) instanceof IntegerType && n.e2.accept(this) instanceof IntegerType) {
      return new IntegerType();
    }

    return new IntegerType();
  }

  // Exp e1,e2;
  public Type visit(ArrayLookup n) {
    if (!(n.e1.accept(this) instanceof IntArrayType)) {
      System.out.printf("Left side of ArrayLookup must be of type integer (%s,%s:%s)%n", 0, 0, 0);
      return null;
    }

    if (!(n.e2.accept(this) instanceof IntegerType)) {
      System.out.printf("Right side of ArrayLookup must be of type integer (%s,%s:%s)%n", 0, 0, 0);
      return null;
    }

    return new IntegerType();
  }

  // Exp e;
  public Type visit(ArrayLength n) {
    if (!(n.e.accept(this) instanceof IntArrayType)) {
      System.out.printf("Left side of ArrayLength must be of type integer (%s,%s:%s)%n", 0, 0, 0);
      return null;
    }

    return new IntegerType();
  }

  // Exp e;
  // Identifier i;
  // ExpList el;
  public Type visit(Call n) {
    Type ne = n.e.accept(this);

    if (!(ne instanceof IdentifierType)) {
      System.err.printf("Method %s should be called on Class or Object, while %s is not a valid class", n.i, n.i);
      return null;
    }

    String methodId = n.i.toString();
    String classId = ((IdentifierType) ne).s;

    ArrayList<Type> paramTypes = new ArrayList<>();
    for (int i = 0; i < n.el.size(); i++) {
      paramTypes.add(n.el.elementAt(i).accept(this));
    }
    Method calledMethod = symbolTable.getMethod(paramTypes, methodId, classId);

    if (calledMethod == null) {
      System.err.printf("Method %s not defined in %s (%s,%s:%s)%n", methodId, classId, 0, 0, 0);
      return null;
    }

    return calledMethod.getType();
  }

  // int i;
  public Type visit(IntegerLiteral n) {
    return new IntegerType();
  }

  public Type visit(True n) {
    return new BooleanType();
  }

  public Type visit(False n) {
    return new BooleanType();
  }

  // String s;
  public Type visit(IdentifierExp n) {
    Type type = symbolTable.getVarType(currMethod, currClass, n.s);
    return type;
  }

  public Type visit(This n) {
    return currClass.getType();
  }

  // Exp e;
  public Type visit(NewArray n) {
    if (!(n.e.accept(this) instanceof IntegerType)) {
      System.err.printf("NewArray operand must be of type boolean (%s,%s:%s)%n", 0, 0, 0);
      return null;
    }

    return new IntArrayType();
  }

  // Identifier i;
  public Type visit(NewObject n) {
    return new IdentifierType(n.i.s);
  }

  // Exp e;
  public Type visit(Not n) {
    if (!(n.e.accept(this) instanceof BooleanType)) {
      System.err.printf("Not operand must be of type boolean (%s,%s:%s)%n", 0, 0, 0);
      return null;
    }

    return new BooleanType();
  }

}