package org.jlab.ersap.actor.helloworld.engine;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.helloworld.proc.HelloWorld;

import java.util.Set;

public class HelloWorldEngine implements Engine {

    private HelloWorld hw;

    @Override
    public EngineData configure(EngineData engineData) {
        hw = new HelloWorld();
        return null;
    }

    @Override
    public EngineData execute(EngineData input) {
        // Todo: Here you are using the old engine that expects integer defines the language
        //       to return Hello World. Here simply you are casting String to integer which
        //       will throw a runtime exception. You have to write some other toy processor that takes
        //       String or int (your file contains integers) does something with that event/data
        //       quantum from a file and returns the result.
        //       here I commented out the engine and simply return the input.
//        if (input.getMimeType().equalsIgnoreCase(EngineDataType.STRING.mimeType())) {
//            EngineData out = new EngineData();
//            out.setData(EngineDataType.STRING,hw.defineHelloWorld((Integer)input.getData()));
//            return out;
//        } else {
            return input;
//        }
    }

    @Override
    public EngineData executeGroup(Set<EngineData> set) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.STRING,
                EngineDataType.JSON);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.STRING);
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return "Simple Hello World actor engine";
    }

    @Override
    public String getVersion() {
        return "v0.1";
    }

    @Override
    public String getAuthor() {
        return "vg";
    }

    @Override
    public void reset() {
    }

    @Override
    public void destroy() {

    }
}
