local acc = require("gng.acc").find()

for k,v in pairs(acc:status()) do
    print(tostring(k).." : "..tostring(v))
end
