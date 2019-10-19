import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-hello3',
  templateUrl: './hello3.component.html',
  styleUrls: ['./hello3.component.css']
})
export class Hello3Component implements OnInit {
  name = 'World';

  constructor() { }

  ngOnInit() {
  }

  onChange() {
    console.log(this.name);
  }

}
