package visitor;

import syntaxtree.*;

import java.util.ArrayList;

public class TypeCheckVisitor extends DepthFirstVisitor {

  static Class currClass;
  static Method currMethod;
  static SymbolTable symbolTable;


  public TypeCheckVisitor(SymbolTable s) {
    symbolTable = s;
  }

  // MainClass m;
  // ClassDeclList cl;
  public void visit(Program n) {
    n.m.accept(this);
    for (int i = 0; i < n.cl.size(); i++) {
      n.cl.elementAt(i).accept(this);
    }
  }

  // Identifier i1,i2;
  // Statement s;
  public void visit(MainClass n) {
    String i1 = n.i1.toString();

    currClass = symbolTable.getClass(i1);
    currMethod = currClass.getMethod(new ArrayList<>(), "main");

    for (int i = 0; i < n.vl.size(); i++) {
      n.vl.elementAt(i).accept(this);
    }

    n.i2.accept(this);
    n.s.accept(this);
  }

  // Identifier i;
  // VarDeclList vl;
  // MethodDeclList ml;
  public void visit(ClassDeclSimple n) {
    String id = n.i.toString();
    currClass = symbolTable.getClass(id);

    for (int i = 0; i < n.vl.size(); i++) {
      n.vl.elementAt(i).accept(this);
    }
    for (int i = 0; i < n.ml.size(); i++) {
      n.ml.elementAt(i).accept(this);
    }
  }

  // Identifier i;
  // Identifier j;
  // VarDeclList vl;
  // MethodDeclList ml;
  public void visit(ClassDeclExtends n) {
    String id = n.i.toString();
    currClass = symbolTable.getClass(id);

    n.j.accept(this);
    for (int i = 0; i < n.vl.size(); i++) {
      n.vl.elementAt(i).accept(this);
    }
    for (int i = 0; i < n.ml.size(); i++) {
      n.ml.elementAt(i).accept(this);
    }
  }

  // Type t;
  // Identifier i;
  public void visit(VarDecl n) {
    n.t.accept(this);
    n.i.accept(this);
  }

  // Type t;
  // Identifier i;
  // FormalList fl;
  // VarDeclList vl;
  // StatementList sl;
  // Exp e;
  public void visit(MethodDecl n) {
    n.t.accept(this);
    String id = n.i.toString();

    ArrayList<Type> paramTypes = new ArrayList<>();
    for (int i = 0; i < n.fl.size(); i++) {
      paramTypes.add(n.fl.elementAt(i).t);
    }
    currMethod = currClass.getMethod(paramTypes, id);

    Type retType = currMethod.getType();
    for (int i = 0; i < n.fl.size(); i++) {
      n.fl.elementAt(i).accept(this);
    }
    for (int i = 0; i < n.vl.size(); i++) {
      n.vl.elementAt(i).accept(this);
    }
    for (int i = 0; i < n.sl.size(); i++) {
      n.sl.elementAt(i).accept(this);
    }

    if (symbolTable.compareTypes(retType, n.e.accept(new TypeCheckExpVisitor())) == false) {
      System.out.println("Wrong return type for method " + id);
      System.exit(0);
    }
  }

  // Type t;
  // Identifier i;
  public void visit(Formal n) {
    n.t.accept(this);
    n.i.accept(this);
  }

  // Exp e;
  // Statement s1,s2;
  public void visit(If n) {
    if (!(n.e.accept(new TypeCheckExpVisitor()) instanceof BooleanType)) {
      System.out.println("The condition of while must be of type boolean");
      System.exit(-1);
    }
    n.s1.accept(this);
    n.s2.accept(this);
  }

  // Exp e;
  // Statement s;
  public void visit(While n) {
    if (!(n.e.accept(new TypeCheckExpVisitor()) instanceof BooleanType)) {
      System.out.println("The condition of while must be of type boolean");
      System.exit(-1);
    }
    n.s.accept(this);
  }

  // Exp e;
  public void visit(Print n) {
    if (!(n.e.accept(new TypeCheckExpVisitor()) instanceof IntegerType)) {
      System.out.println("The argument of System.out.println must be of type int");
      System.exit(-1);
    }
  }

  // Identifier i;
  // Exp e;
  public void visit(Assign n) {
    Type t1 = symbolTable.getVarType(currMethod, currClass, n.i.toString());
    Type t2 = n.e.accept(new TypeCheckExpVisitor());

    if (t1 == null) {
      System.err.printf("%s: Unknown identifier (%s,%s:%s)%n", n.i.toString(), 0, 0, currMethod.getInternalId());
      return;
    }

    if (symbolTable.compareTypes(t1, t2) == false) {
      System.out.println("Type error in assignment to " + n.i.toString());
      System.exit(0);
    }
  }

  // Identifier i;
  // Exp e1,e2;
  public void visit(ArrayAssign n) {
    Type typeI = symbolTable.getVarType(currMethod, currClass, n.i.toString());

    if (typeI == null) {
      System.err.printf("%s: Unknown identifier (%s,%s:%s)%n", n.i.toString(), 0, 0, currMethod.getInternalId());
      return;
    }

    if (!(typeI instanceof IntArrayType)) {
      System.out.printf("The identifier in an array assignment must be of type int [] (%s,%s:%s)%n", 0, 0, currMethod.getInternalId());
      System.exit(-1);
    }

    if (!(n.e1.accept(new TypeCheckExpVisitor()) instanceof IntegerType)) {
      System.out.printf("The first expression in an array assignment must be of type int (%s,%s:%s)%n", 0, 0, currMethod.getInternalId());
      System.exit(-1);
    }

    if (!(n.e1.accept(new TypeCheckExpVisitor()) instanceof IntegerType)) {
      System.out.printf("The second expression in an array assignment must be of type int (%s,%s:%s)%n", 0, 0, currMethod.getInternalId());
      System.exit(-1);
    }
  }
}

