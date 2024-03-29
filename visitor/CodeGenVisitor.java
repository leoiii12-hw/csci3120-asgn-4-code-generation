package visitor;

import syntaxtree.*;

import java.io.PrintWriter;
import java.util.ArrayList;

public class CodeGenVisitor extends DepthFirstVisitor {

  static Class currClass;
  static Method currMethod;
  static SymbolTable symbolTable;
  PrintWriter out;

  public CodeGenVisitor(SymbolTable s, PrintWriter out) {
    symbolTable = s;
    this.out = out;
  }

  // MainClass m;
  // ClassDeclList cl;
  public void visit(Program n) {
    // Data segment 
    out.println(
      ".data\n" +
        "newline: .asciiz \"\\n\"\n" +    // to be used by cgen for "System.out.println()"
        "msg_index_out_of_bound_exception: .asciiz \"Index out of bound exception\\n\"\n" +
        "msg_null_pointer_exception: .asciiz \"Null pointer exception\\n\"\n" +
        "\n" +
        ".text\n"
    );

    n.m.accept(this);

    out.println(  // Code to terminate the program
      "# exit\n" +
        "li $v0, 10\n" +
        "syscall\n"
    );

    // Code for all methods
    for (int i = 0; i < n.cl.size(); i++) {
      n.cl.elementAt(i).accept(this);
    }

    // Code for some utility functions 
    cgen_supporting_functions();
  }

  // Identifier i1,i2;
  // VarDeclList vl;
  // Statement s;
  public void visit(MainClass n) {
    String i1 = n.i1.toString();

    currClass = symbolTable.getClass(i1);
    currMethod = currClass.getMethod(new ArrayList<>(), "main");   // This is a hack (treat main() as instance method.)

    // Can ignore the parameter of main()

    // Info about local variables are kept in "currMethod"
    for (int i = 0; i < n.vl.size(); i++) {
      n.vl.elementAt(i).accept(this);
    }

    // Generate code to reserve space for local variables in stack
    // Optionally, generate code to reserve space for temps

    n.s.accept(this);
  }

  // Identifier i;
  // VarDeclList vl;
  // MethodDeclList ml;
  public void visit(ClassDeclSimple n) {
    currClass = symbolTable.getClass(n.i.s);

    int numOfFields = currClass.fields.size();

    // _alloc_Foo
    // $v0 = object address

    out.print("" +
      "_alloc_" + currClass.getId() + ":\n" +

      "li $a0, " + (1 + numOfFields) * 4 + "\n" +
      "li $v0, 9\n" +
      "syscall\n" +

      // Store size
      "sw $a0, 0($v0)\n");

    // Initialize fields
    out.print("li $t0, 0\n");
    for (int i = 0; i < numOfFields; i++) {
      out.print("sw $t0, " + (i * 4 + 4) + "($v0)\n");
    }

    // Return
    out.print("" +
      "jr $ra\n\n");

    for (int i = 0; i < n.ml.size(); i++) {
      n.ml.elementAt(i).accept(this);
    }

    currClass = null;
  }

  // Type t;
  // Identifier i;
  // FormalList fl;
  // VarDeclList vl;
  // StatementList sl;
  // Exp e;
  // cgen: t i(fl) { vl sl return e; }
  public void visit(MethodDecl n) {
    String id = n.i.toString();

    ArrayList<Type> paramTypes = new ArrayList<>();
    for (int i = 0; i < n.fl.size(); i++) {
      paramTypes.add(n.fl.elementAt(i).t);
    }
    currMethod = currClass.getMethod(paramTypes, id);

    out.print("" +
      "_fun_" + currMethod.getUniqueId() + ":\n" +
      "move $fp, $sp\n" +
      "sw $ra, 0($sp)\n" +
      "addiu $sp, $sp, -4\n");

    out.print("# cgen(body)\n");

    // vl
    for (int i = 0; i < n.vl.size(); i++) {
      n.vl.elementAt(i).accept(this);
    }

    // sl
    for (int i = 0; i < n.sl.size(); i++) {
      n.sl.elementAt(i).accept(this);
    }

    // return
    n.e.accept(this);
    out.print("move $v0, $a0\n");

    out.print("# cgen(body) end\n");

    // The first one is the object
    int numOfArguments = 1 + currMethod.params.size(); // With object itselft as the first argument
    out.print("" +
      "lw $ra, 4($sp)\n" +
      "addiu $sp, $sp, " + (numOfArguments * 4 + 8) + "\n" +
      "lw $fp, 0($sp)\n" +
      "jr $ra\n\n");
  }

  private static int numOfIf = 0;

  // Exp e;
  // Statement s1,s2;
  // cgen: if (e) s1 else s2
  public void visit(If n) {
    n.e.accept(this);

    String trueBranchName = "_true_" + (numOfIf);
    String falseBranchName = "_false_" + (numOfIf);
    String endIfBranchName = "_end_if_" + (numOfIf);
    numOfIf++;

    out.print("" +
      "# public void visit(If n)\n" +
      "bne $a0, $0, " + trueBranchName + " \n" +
      "beq $a0, $0, " + falseBranchName + " \n\n");

    out.print("" +
      "# true branch\n" +
      trueBranchName + ":\n");
    n.s1.accept(this);
    out.print("j " + endIfBranchName + "\n\n");

    out.println("" +
      "# false branch\n" +
      falseBranchName + ":\n");
    n.s2.accept(this);
    out.print("j " + endIfBranchName + "\n\n");

    out.print(endIfBranchName + ":\n\n");
  }

  private static int numOfWhile = 0;

  // Exp e;
  // Statement s;
  // cgen: while (e) s;
  public void visit(While n) {
    String whileBranchName = "_while_" + (numOfWhile);
    String endWhileBranchName = "_end_while_" + (numOfWhile);
    numOfWhile++;

    out.print("" +
      "# public void visit(While n)\n" +
      whileBranchName + ":\n");

    n.e.accept(this);
    out.print("beq $a0, $0, " + endWhileBranchName + " # while condition\n");

    n.s.accept(this);
    out.print("j " + whileBranchName + " # while jump\n");

    out.print(endWhileBranchName + ":\n");

    out.print("# public void visit(While n) end\n\n");
  }

  // Exp e;
  // cgen: System.out.println(e)
  public void visit(Print n) {
    n.e.accept(this);

    out.print("jal _print_int\n");
  }

  // Identifier i;
  // Exp e;
  // cgen: i = e
  public void visit(Assign n) {
    out.print("" +
      "# public void visit(Assign n)\n");

    if (currMethod.containsParam(n.i.s) || currMethod.containsVar(n.i.s)) {
      int internalId = getVarInternalId(n.i.s);

      // Evaluate the assigning value
      n.e.accept(this);

      out.print("sw $a0, " + (internalId) * 4 + "($fp)\n");
    } else {
      n.e.accept(this);

      out.print("" +
        "lw $t0, " + (currMethod.params.size() * 4 + 4) + "($fp)\n" +
        "sw $a0, " + (currClass.getVar(n.i.s).getInternalId() * 4) + "($t0)\n");
    }

    out.print("" +
      "# public void visit(Assign n) end\n\n");
  }

  // Identifier i;
  // Exp e1,e2;
  // cgen: i[e1] = e2
  public void visit(ArrayAssign n) {
    out.print("# public void visit(ArrayAssign n)\n");

    // Evaluate the assigning value
    n.e2.accept(this);
    out.print("move $a2, $a0 # Store assigning value in $a2\n");

    // Calculate the relative address
    n.e1.accept(this);
    out.print("" +
      "addi $a0, $a0, 1 # $a0 is the calculated index\n" +

      "li $t1, 4\n" +
      "mul $t1, $t1, $a0 # multiply 4 as words and store in $t1\n");

    if (currMethod.containsParam(n.i.s) || currMethod.containsVar(n.i.s)) {
      int internalId = getVarInternalId(n.i.s);

      // Save
      out.print("" +
        "lw $a1, " + (internalId) * 4 + "($fp) # base array address\n" +
        "add $t0, $a1, $t1 # calculate the actual address\n" +

        "sw $a2, 0($t0) # save value\n");
    } else {
      out.print("" +
        "lw $t0, " + (currMethod.params.size() * 4 + 4) + "($fp)\n" +
        "lw $a1, " + (currClass.getVar(n.i.s).getInternalId() * 4) + "($t0) # base array address\n" +
        "add $t0, $a1, $t1 # calculate the actual address\n" +

        "sw $a2, 0($t0) # save value\n");
    }

    out.print("# public void visit(ArrayAssign n) end\n\n");
  }

  // Exp e1,e2;
  // cgen: e1 && e2
  public void visit(And n) {
    n.e1.accept(this);

    out.print("" +
      "# public void visit(And n)\n" +
      "addiu $sp, $sp, -4\n" +
      "sw $a0, 4($sp)\n");

    n.e2.accept(this);

    out.print("" +
      "lw $t0, 4($sp)\n" +
      "and $a0, $t0, $a0\n" +
      "addiu $sp, $sp, 4\n\n");
  }

  // Exp e1,e2;
  // cgen: e1 < e2
  public void visit(LessThan n) {
    n.e1.accept(this);

    out.print("" +
      "# public void visit(LessThan n)\n" +
      "addiu $sp, $sp, -4\n" +
      "sw $a0, 4($sp)\n");

    n.e2.accept(this);

    out.print("" +
      "lw $t0, 4($sp)\n" +
      "slt $a0, $t0, $a0\n" +
      "addiu $sp, $sp, 4\n\n");
  }

  // Exp e1,e2;
  // cgen: e1 + e2
  public void visit(Plus n) {
    n.e1.accept(this);

    out.print("" +
      "# public void visit(Plus n)\n" +
      "addiu $sp, $sp, -4\n" +
      "sw $a0, 4($sp)\n");

    n.e2.accept(this);

    out.print("" +
      "lw $t0, 4($sp)\n" +
      "add $a0, $t0, $a0\n" +
      "addiu $sp, $sp, 4\n\n");
  }

  // Exp e1,e2;
  // cgen: e1 - e2
  public void visit(Minus n) {
    n.e1.accept(this);

    out.print("" +
      "# public void visit(Minus n)\n" +
      "addiu $sp, $sp, -4\n" +
      "sw $a0, 4($sp)\n");

    n.e2.accept(this);

    out.print("" +
      "lw $t0, 4($sp)\n" +
      "sub $a0, $t0, $a0\n" +
      "addiu $sp, $sp, 4\n\n");
  }

  // Exp e1,e2;
  // cgen: e1 * e2
  public void visit(Times n) {
    n.e1.accept(this);

    out.print("" +
      "# public void visit(Times n)\n" +
      "addiu $sp, $sp, -4\n" +
      "sw $a0, 4($sp)\n");

    n.e2.accept(this);

    out.print("" +
      "lw $t0, 4($sp)\n" +
      "mul $a0, $t0, $a0\n" +
      "addiu $sp, $sp, 4\n\n");
  }

  // Exp e1,e2;
  // cgen: e1[e2]
  public void visit(ArrayLookup n) {
    // $a1 is the base address
    n.e1.accept(this);
    out.print("" +
      "move $a1, $a0\n");

    // $a0 is the index
    n.e2.accept(this);

    out.print("" +
      "# public void visit(ArrayLookup n)\n" +
      "addi $a0, $a0, 1 # $a0 is the calculated index\n" +
      "li $t1, 4\n" +
      "mul $t1, $t1, $a0 # multiply 4 as words\n" +
      "add $t0, $a1, $t1\n" +
      "lw $a0, 0($t0) # load value\n\n");
  }

  // Exp e;
  // cgen: e.length
  public void visit(ArrayLength n) {
    // $a1 is the base address
    n.e.accept(this);
    out.print("" +
      "move $a1, $a0\n");

    out.print("" +
      "# public void visit(ArrayLength n)\n" +
      "lw $a0, 0($a1)\n\n");
  }

  // Exp e;
  // Identifier i;
  // ExpList el;
  // cgen: e.i(el)
  public void visit(Call n) {
    ArrayList<Type> paramTypes = new ArrayList<>();
    for (int i = 0; i < n.el.size(); i++) {
      Exp param = n.el.elementAt(i);
      paramTypes.add(getExpType(param));
    }

    Class clazz = symbolTable.getClass(((IdentifierType) getExpType(n.e)).s);
    Method method = clazz.getMethod(paramTypes, n.i.s);

    out.print("" +
      "# public void visit(Call n)\n" +
      "sw $fp, 0($sp)\n" +
      "addiu $sp, $sp, -4\n"
    );

    // Get object address in $a0
    n.e.accept(this);
    out.print("" +
      "sw $a0, 0($sp) # get object address\n" +
      "addiu $sp, $sp, -4\n");

    // Reverse order
    for (int i = n.el.size() - 1; i >= 0; i--) {
      n.el.elementAt(i).accept(this);

      out.print("" +
        "sw $a0, 0($sp) # formal " + i + "\n" +
        "addiu $sp, $sp, -4\n");
    }

    out.print("" +
      "jal " + "_fun_" + method.getUniqueId() + "\n" +
      "move $a0, $v0\n" +
      "# public void visit(Call n) end\n\n");
  }

  // Exp e;
  // cgen: new int [e]
  public void visit(NewArray n) {
    n.e.accept(this);

    out.print("" +
      "# public void visit(NewArray n)\n" +
      "jal _alloc_int_array\n" +
      "move $a0, $v0\n");
  }

  // Identifier i;
  // cgen: new n
  public void visit(NewObject n) {
    Class newObjectClass = symbolTable.getClass(n.i.s);

    out.print("" +
      "jal _alloc_" + newObjectClass.getId() + "\n" +
      "move $a0, $v0\n");
  }

  // Exp e;
  // cgen: !e
  public void visit(Not n) {
    n.e.accept(this);

    out.print("" +
      "# public void visit(Not n)\n" +
      "li $t0, 1\n" +
      "not $a0, $a0\n" +
      "and $a0, $a0, $t0\n");
  }

  // cgen: this
  public void visit(This n) {
  }

  // int i;
  // cgen: Load immediate the value of n.i
  public void visit(IntegerLiteral n) {
    out.print("" +
      "# public void visit(IntegerLiteral n)\n" +
      "li $a0, " + n.i + "\n");
  }

  // cgen: Load immeidate the value of "true"
  public void visit(True n) {
    out.print("" +
      "# public void visit(True n)\n" +
      "li $a0, 1\n");
  }

  // cgen: Load immeidate the value of "false"
  public void visit(False n) {
    out.print("" +
      "# public void visit(False n)\n" +
      "li $a0, 0\n");
  }

  // String s;
  // cgen: Load the value of the variable n.s (which can be a local variable, parameter, or field)
  public void visit(IdentifierExp n) {
    if (currMethod.containsParam(n.s) || currMethod.containsVar(n.s)) {
      int internalId = getVarInternalId(n.s);
      out.print("lw $a0, " + (internalId * 4) + "($fp) # public void visit(IdentifierExp n)\n");
    } else {
      out.print("lw $t0, " + (currMethod.params.size() * 4 + 4) + "($fp) # public void visit(IdentifierExp n)\n");
      out.print("lw $a0, " + (currClass.getVar(n.s).getInternalId() * 4) + "($t0)\n");
    }
  }

  private void cgen_supporting_functions() {
    out.println("" +
      "_print_int:      # System.out.println(int)\n" +

      "li $v0, 1      # System service = 1\n" +
      "syscall\n" +

      "li $v0, 4      # print newline\n" +
      "la $a0, newline\n" +
      "syscall\n" +

      "jr $ra\n"
    );

    out.println("" +
      "_null_pointer_exception:\n" +
      "la $a0, msg_null_pointer_exception\n" +
      "li $a1, 23\n" +
      "li $v0, 4\n" +
      "syscall\n" +
      "li $v0, 10\n" +
      "syscall\n"
    );

    out.println("" +
      "_array_index_out_of_bound_exception:\n" +
      "la $a0, msg_index_out_of_bound_exception\n" +
      "li $a1, 29\n" +
      "li $v0, 4\n" +
      "syscall\n" +
      "li $v0, 10\n" +
      "syscall\n"
    );

    out.println("" +
      "_alloc_int_array: # new int [$a0]\n" +
      "addi $a2, $a0, 0  # Save length in $a2\n" +
      "addi $a0, $a0, 1  # One more word to store the length\n" +
      "sll $a0, $a0, 2   # multiple by 4 bytes\n" +
      "li $v0, 9         # allocate space\n" +
      "syscall\n" +
      "" +
      "sw $a2, 0($v0)    # Store array length\n" +
      "addi $t1, $v0, 4  # begin address = ($v0 + 4); address of the first element\n" +
      "add $t2, $v0, $a0 # loop until ($v0 + 4*(length+1)), the address after the last element\n" +
      "" +
      "_alloc_int_array_loop:\n" +
      "beq $t1, $t2, _alloc_int_array_loop_end\n" +
      "sw $0, 0($t1)\n" +
      "addi $t1, $t1, 4\n" +
      "j _alloc_int_array_loop\n" +
      "" +
      "_alloc_int_array_loop_end:\n" +
      "\n" +
      "jr $ra\n"
    );
  }

  private Type getExpType(Exp e) {
    return e.accept(new TypeCheckExpVisitor(currClass, currMethod, symbolTable));
  }

  private int getVarInternalId(String s) {
    Variable var = null;
    int internalId = 0;

    if (currMethod.containsVar(s)) {
      var = currMethod.getVar(s);
      internalId = var.getInternalId() * -1;
    } else if (currMethod.containsParam(s)) {
      var = currMethod.getParam(s);
      internalId = var.getInternalId();
    } else if (currClass.containsVar(s)) {
      // TODO
    } else {
      System.err.println("Uncaught getVarInternalId(String s)");
    }

    return internalId;
  }

}

