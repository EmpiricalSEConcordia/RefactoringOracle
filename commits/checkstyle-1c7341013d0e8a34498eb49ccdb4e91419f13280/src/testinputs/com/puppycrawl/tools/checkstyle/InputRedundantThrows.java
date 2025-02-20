import java.io.IOException;

public class InputRedundantThrows
{
    // wrong contains subclasses
    InputRedundantThrows()
        throws java.io.IOException, java.io.FileNotFoundException
    {
    }

    // wrong uncheck exception
    void method1()
        throws RuntimeException
    {
    }

    // wrong - duplicate
    void method2()
        throws IOException, java.io.IOException
    {
    }

    // bad - no information for checking exception
    void method3()
        throws WrongException
    {
    }

    // right
    void method4()
        throws IOException, ClassNotFoundException
    {
    }
}
